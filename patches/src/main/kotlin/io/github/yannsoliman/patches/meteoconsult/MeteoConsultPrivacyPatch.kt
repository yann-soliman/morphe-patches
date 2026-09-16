package io.github.yannsoliman.patches.meteoconsult

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val CONFIGURATION_CONTENT =
    "Lcom/meteoconsult/androidapp/network/model/configuration/ConfigurationContent;"
private const val ANALYTICS_CONTEXT = "Landroid/content/Context;"
private const val ANALYTICS_EVENT = "Lfk/e;"
private const val ADVERTISING_MANAGER =
    "Lcom/lachainemeteo/advertisingmanager/AdvertisingManager;"
private const val PURCHASALY_INITIALIZER_SUFFIX = "/PurchaselyInitializer;"

private const val FUNCTION2 = "Lkotlin/jvm/functions/Function2;"

private fun analyticsGetter(name: String) = Fingerprint(
    definingClass = CONFIGURATION_CONTENT,
    name = name,
    returnType = "Z",
    parameters = emptyList(),
)

private val advertisingDataFingerprint = Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = ADVERTISING_MANAGER,
            name = "setData",
            parameters = listOf(
                ANALYTICS_CONTEXT,
                "Ljava/util/List;",
                "Ljava/util/HashMap;",
            ),
            returnType = "V",
        )
    )
)

@Suppress("unused")
val meteoConsultPrivacyPatch = bytecodePatch(
    name = "Meteo Consult: privacy mode",
    description = "Disable analytics, crash reports, and background initialization of the paywall and advertising SDKs. Weather data, location, Firebase Messaging, and weather alerts remain enabled. Meteo Consult 1.1.4 only."
) {
    compatibleWith(Compatibility(
        name = "Meteo Consult",
        packageName = "com.meteoconsult.androidapp",
        apkFileType = ApkFileType.XAPK,
        targets = listOf(AppTarget(version = "1.1.4"))
    ))

    execute {
        // These are deliberately separate fingerprints: all three remote
        // configuration flags must still have their original getter layout.
        val analyticsGetters = listOf(
            analyticsGetter("isAnalyticsActive").matchAll(1..1).single(),
            analyticsGetter("isAnalyticsNcActive").matchAll(1..1).single(),
            analyticsGetter("isWysistatActive").matchAll(1..1).single(),
        )

        analyticsGetters.forEach { match ->
            val method = match.method
            val instructions = method.implementation!!.instructions.toList()
            check(instructions.map { it.opcode } == listOf(Opcode.IGET_BOOLEAN, Opcode.RETURN)) {
                "Unexpected ${method.name} getter layout"
            }
            method.removeInstructions(0, instructions.size)
            method.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
        }

        // This is the event dispatcher, rather than a weather/location path.
        // Keep both strings in the fingerprint because either one alone is too
        // common in a Kotlin/Android application.
        val eventDispatcher = Fingerprint(
            definingClass = "Luh/a;",
            name = "K",
            returnType = "V",
            parameters = listOf(ANALYTICS_CONTEXT, ANALYTICS_EVENT),
            filters = listOf(string("screenName"), string("screen_view")),
        ).matchAll(1..1).single()
        check(eventDispatcher.instructionMatches.size == 2) {
            "Unexpected analytics event dispatcher fingerprint"
        }
        val eventInstructions = eventDispatcher.method.implementation!!.instructions.toList()
        check(eventInstructions.isNotEmpty()) { "Analytics event dispatcher has no body" }
        eventDispatcher.method.removeInstructions(0, eventInstructions.size)
        eventDispatcher.method.addInstructions(0, "return-void")

        // Keep the registrar's already-created empty component list. This
        // prevents Crashlytics registration while leaving Firebase Messaging
        // initialization and its alert delivery untouched.
        val crashlytics = Fingerprint(
            definingClass = "Lcom/google/firebase/crashlytics/CrashlyticsRegistrar;",
            name = "getComponents",
            returnType = "Ljava/util/List;",
            parameters = emptyList(),
            filters = listOf(string("fire-cls")),
        ).matchAll(1..1).single()
        val crashInstructions = crashlytics.method.implementation!!.instructions.toList()
        check(crashlytics.instructionMatches.size == 1)
        check(crashlytics.instructionMatches.single().instruction.opcode in
            listOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO)) {
            "Crashlytics registrar version marker is not a string literal"
        }
        check(crashInstructions.lastOrNull()?.opcode == Opcode.RETURN_OBJECT) {
            "Unexpected Crashlytics registrar return layout"
        }
        crashlytics.method.removeInstructions(0, crashInstructions.size)
        crashlytics.method.addInstructions(
            0,
            "sget-object v0, Lnr/q;->a:Lnr/q;\nreturn-object v0"
        )

        // AndroidX Startup calls this method with a builder/callback. Only
        // remove Purchasely.start: the Unit return and builder setup remain.
        val purchaselyInitializer = Fingerprint(
            name = "create",
            returnType = "Ljava/lang/Object;",
            parameters = listOf(ANALYTICS_CONTEXT),
            custom = { method, _ ->
                method.definingClass.endsWith(PURCHASALY_INITIALIZER_SUFFIX)
            },
            filters = listOf(
                methodCall(
                    name = "start",
                    parameters = listOf(FUNCTION2),
                    returnType = "V",
                )
            ),
        ).matchAll(1..1).single()
        val startMatch = purchaselyInitializer.instructionMatches.single()
        val startReference =
            ((startMatch.instruction as? ReferenceInstruction)?.reference as? MethodReference)
                ?: error("Purchasely.start match has no method reference")
        check(startReference.name == "start" &&
            startReference.parameterTypes.map { it.toString() } == listOf(FUNCTION2) &&
            startReference.returnType == "V" &&
            startReference.definingClass.substringAfterLast('/').removeSuffix(";") == "Purchasely"
        ) { "Unexpected Purchasely.start reference" }
        check(startMatch.instruction.opcode == Opcode.INVOKE_STATIC) {
            "Purchasely.start is not a static call"
        }
        val initializerInstructions = purchaselyInitializer.method.implementation!!.instructions.toList()
        check(initializerInstructions.drop(startMatch.index + 1).any { it.opcode == Opcode.RETURN_OBJECT }) {
            "Purchasely initializer no longer returns Unit"
        }
        purchaselyInitializer.method.replaceInstruction(startMatch.index, "nop")

        // Resolve exactly the two advertising-manager writes and verify their
        // owning methods before editing. This does not touch getAdForSpace or
        // getCountProviderForSpace from the cleanup patch.
        val advertisingWrites = advertisingDataFingerprint.matchAll(2..2).toList()
        check(advertisingWrites.map { it.method.definingClass to it.method.name }.toSet() == setOf(
            "Lkm/q;" to "c",
            "Lvl/j;" to "a",
        )) { "Unexpected AdvertisingManager.setData callers" }
        advertisingWrites.forEach { match ->
            check(match.instructionMatches.size == 1)
            val reference =
                ((match.instructionMatches.single().instruction as? ReferenceInstruction)
                    ?.reference as? MethodReference)
                    ?: error("AdvertisingManager.setData match has no method reference")
            check(reference.definingClass == ADVERTISING_MANAGER &&
                reference.name == "setData" &&
                reference.parameterTypes.map { it.toString() } == listOf(
                    ANALYTICS_CONTEXT,
                    "Ljava/util/List;",
                    "Ljava/util/HashMap;",
                ) && reference.returnType == "V") {
                "Unexpected AdvertisingManager.setData reference"
            }
            match.method.replaceInstruction(match.instructionMatches.single().index, "nop")
        }
    }
}
