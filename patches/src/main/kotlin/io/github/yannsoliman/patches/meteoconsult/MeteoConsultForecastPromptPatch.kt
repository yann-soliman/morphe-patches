package io.github.yannsoliman.patches.meteoconsult

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val LICENSE_PROVIDER = "Lcom/pairip/licensecheck/LicenseContentProvider;"
private const val LICENSE_CLIENT = "Lcom/pairip/licensecheck/LicenseClient;"

private fun endEvent(label: String) = Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(string(label))
)

@Suppress("unused")
val meteoConsultForecastPromptPatch = bytecodePatch(
    name = "Meteo Consult: hide forecast end subscription prompt",
    description = "Stop the forecast end subscription prompt and the startup Play installation check for the locally signed app. Does not unlock or extend forecasts. Meteo Consult 1.1.4 only."
) {
    compatibleWith(Compatibility(
        name = "Meteo Consult",
        packageName = "com.meteoconsult.androidapp",
        apkFileType = ApkFileType.XAPK,
        targets = listOf(AppTarget(version = "1.1.4"))
    ))
    execute {
        // This provider runs before the app UI. Its Play installation check can
        // redirect a locally installed/re-signed build to the Play Store.
        val startup = Fingerprint(
            name = "onCreate",
            returnType = "Z",
            parameters = emptyList(),
            custom = { method, _ -> method.definingClass == LICENSE_PROVIDER },
            filters = listOf(
                methodCall(definingClass = LICENSE_CLIENT, name = "<init>",
                    parameters = listOf("Landroid/content/Context;"), returnType = "V"),
                methodCall(definingClass = LICENSE_CLIENT, name = "initializeLicenseCheck",
                    parameters = emptyList(), returnType = "V")
            )
        ).matchAll(1..1).single()
        val startupInstructions = startup.method.implementation!!.instructions.toList()
        check(startupInstructions.map { it.opcode } == listOf(
            Opcode.NEW_INSTANCE, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT,
            Opcode.INVOKE_DIRECT, Opcode.INVOKE_VIRTUAL, Opcode.CONST_4, Opcode.RETURN
        )) { "Unexpected Play installation provider layout" }
        check(startup.instructionMatches.map { it.index } == listOf(3, 4))
        check((startupInstructions[5] as NarrowLiteralInstruction).narrowLiteral == 1)
        check((startupInstructions[5] as OneRegisterInstruction).registerA ==
            (startupInstructions[6] as OneRegisterInstruction).registerA)

        // Resolve semantic event classes instead of depending on obfuscated names.
        val bulletin = endEvent("BulletinEndReached")
            .matchAll(1..1).single().method.definingClass
        val comparator = endEvent("ComparatorEndReached")
            .matchAll(1..1).single().method.definingClass
        val callback = Fingerprint(
            name = "invoke",
            returnType = "Ljava/lang/Object;",
            parameters = listOf("Ljava/lang/Object;"),
            filters = listOf(
                fieldAccess(definingClass = bulletin, type = bulletin, opcode = Opcode.SGET_OBJECT),
                methodCall(definingClass = "Ljava/lang/Object;", name = "equals"),
                methodCall(definingClass = "Ljava/lang/Boolean;", name = "booleanValue"),
                methodCall(
                    definingClass = "Lkotlin/jvm/functions/Function0;",
                    name = "invoke",
                    parameters = emptyList(),
                    returnType = "Ljava/lang/Object;",
                    opcodes = listOf(Opcode.INVOKE_INTERFACE)
                ),
                fieldAccess(definingClass = comparator, type = comparator, opcode = Opcode.SGET_OBJECT)
            )
        ).matchAll(1..1).single()

        val hits = callback.instructionMatches
        val start = hits[0].index
        check(hits.map { it.index - start } == listOf(0, 1, 7, 10, 12)) {
            "Unexpected forecast end handler layout"
        }
        val instructions = callback.method.implementation!!.instructions.toList()
        val expected = listOf(
            Opcode.SGET_OBJECT, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT, Opcode.IF_EQZ,
            Opcode.INVOKE_INTERFACE, Opcode.MOVE_RESULT_OBJECT, Opcode.CHECK_CAST,
            Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT, Opcode.IF_EQZ,
            Opcode.INVOKE_INTERFACE, Opcode.GOTO, Opcode.SGET_OBJECT
        )
        check(instructions.drop(start).take(expected.size).map { it.opcode } == expected) {
            "Unexpected forecast end handler instructions"
        }

        // The ignored-result callback only opens the subscription screen.
        // Keep restriction checks, navigation, comparator handling and API requests intact.
        callback.method.replaceInstruction(hits[3].index, "nop")
        // All targets have been validated. Keep the provider and its true return,
        // suppress only its asynchronous startup check. Purchasely/Billing is untouched.
        startup.method.replaceInstruction(4, "nop")
    }
}
