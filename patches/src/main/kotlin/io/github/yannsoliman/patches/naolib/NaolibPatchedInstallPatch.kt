package io.github.yannsoliman.patches.naolib

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val PAIRIP_APPLICATION = "Lcom/pairip/application/Application;"
private const val LICENSE_CLIENT = "Lcom/pairip/licensecheck/LicenseClient;"

/** Required internally by every public Naolib patch. */
val naolibPatchedInstallPatch = bytecodePatch {
    execute {
        val startup = Fingerprint(
            definingClass = PAIRIP_APPLICATION,
            name = "attachBaseContext",
            returnType = "V",
            parameters = listOf("Landroid/content/Context;"),
            filters = listOf(
                methodCall(
                    definingClass = LICENSE_CLIENT,
                    name = "checkLicense",
                    parameters = listOf("Landroid/content/Context;"),
                    returnType = "V",
                    opcodes = listOf(Opcode.INVOKE_STATIC),
                ),
            ),
        ).matchAll(1..1).single()

        val method = startup.method
        val instructions = method.implementation!!.instructions.toList()
        check(instructions.map { it.opcode } == listOf(
            Opcode.INVOKE_STATIC,
            Opcode.INVOKE_SUPER,
            Opcode.RETURN_VOID,
        )) { "Unexpected Naolib PairIP application startup layout" }
        check(startup.instructionMatches.single().index == 0) {
            "Unexpected Naolib PairIP license call position"
        }

        // Morphe re-signs and reinstalls the app, so PairIP would reject its
        // installer/signature before Naolib starts and open the Play Store.
        // Keep the real application attachBaseContext call and skip only PairIP.
        method.replaceInstruction(0, "nop")
    }
}
