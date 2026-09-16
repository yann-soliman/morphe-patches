package io.github.yannsoliman.patches.meteoconsult

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode

private const val CONFIGURATION_CONTENT =
    "Lcom/meteoconsult/androidapp/network/model/configuration/ConfigurationContent;"
private const val ANALYTICS_CONTEXT = "Landroid/content/Context;"
private const val ANALYTICS_EVENT = "Lfk/e;"

private fun analyticsGetter(name: String) = Fingerprint(
    definingClass = CONFIGURATION_CONTENT,
    name = name,
    returnType = "Z",
    parameters = emptyList(),
)

@Suppress("unused")
val meteoConsultPrivacyPatch = bytecodePatch(
    name = "Meteo Consult: privacy mode",
    description = "Disable application analytics and Wysistat tracking while preserving SDK initialization required at startup. Weather data, location, Firebase Messaging, and weather alerts remain enabled. Meteo Consult 1.1.4 only."
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
    }
}
