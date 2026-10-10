package io.github.yannsoliman.patches.naolib

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction30t
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val naolibBicycleDistancePatch = bytecodePatch(
    name = "Naolib vélo : déverrouiller les vélos à distance",
    description = "Retire le contrôle de proximité lors de la sélection d’une station et avant le déverrouillage d’un vélo. La localisation et un accès valide au service restent nécessaires. Compatible avec Naolib vélo 3.6.1.",
    default = true,
) {
    dependsOn(naolibPatchedInstallPatch)
    compatibleWith(Compatibility(
        name = "Naolib vélo", packageName = "com.jcdecaux.vls.nantes",
        apkFileType = ApkFileType.XAPK, targets = listOf(AppTarget(version = "3.6.1")),
    ))

    execute {
        val location = "Lcom/jcdecaux/cyclocity/vls/core/domain/model/map/Location;"
        val checks = listOf(
            Triple(mutableClassDefBy("Lcl/g\$a;").methods.single {
                it.name == "t" && it.parameterTypes == listOf("Lcl/g\$b;", location)
            }, 29, 37),
            Triple(mutableClassDefBy("Lim/a;").methods.single {
                it.name == "a" && it.parameterTypes == listOf("Lih/a;", "Ljava/util/List;", location)
            }, 77, 79),
        )
        checks.forEachIndexed { index, (method, rejection, success) ->
            val body = method.implementation!!
            val instructions = body.instructions.toList()
            check(instructions.size == if (index == 0) 42 else 90) { "Unexpected bicycle proximity method size" }
            check(instructions[rejection].opcode == if (index == 0) Opcode.IF_LEZ else Opcode.IF_LTZ) {
                "Unexpected bicycle proximity branch"
            }
            check(instructions[rejection - 1].opcode == Opcode.CMPL_FLOAT)
            val threshold = (instructions[rejection - 4] as ReferenceInstruction).reference as MethodReference
            check(threshold.definingClass == "Lph/d;" && threshold.name == "b" && threshold.returnType == "I") {
                "Unexpected bicycle proximity threshold"
            }
            val exceptionIndex = if (index == 0) 30 else 84
            val exception = (instructions[exceptionIndex] as ReferenceInstruction).reference as TypeReference
            check(exception.type == "Lcom/jcdecaux/cyclocity/vls/domain/map/exception/LocationException\$Proximity;")
            val accepted = (instructions[success] as ReferenceInstruction).reference as MethodReference
            check(accepted.definingClass == if (index == 0) "Lwt/a;" else "Lwt/u;")
            check(accepted.name == if (index == 0) "f" else "s")
            // Keep station selection, location acquisition and the normal rental
            // workflow. Only the distance comparison's rejection is bypassed.
            val target = body.newLabelForIndex(success)
            body.replaceInstruction(rejection, BuilderInstruction30t(Opcode.GOTO_32, target))
        }
    }
}
