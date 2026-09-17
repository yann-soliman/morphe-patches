package io.github.yannsoliman.patches.naolib

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val DISTANCE_CHECK = "Lfk/c\$a\$a;"
private const val LOCATION =
    "Lcom/jcdecaux/cyclocity/vls/core/domain/model/map/Location;"
private const val PROXIMITY_EXCEPTION =
    "Lcom/jcdecaux/cyclocity/vls/domain/map/exception/LocationException\$Proximity;"
private const val PARK_REPOSITORY = "Lmk/k;"

@Suppress("unused")
val naolibParkingDistancePatch = bytecodePatch(
    name = "Naolib vélo: remove parking distance limit",
    description = "Remove the client-side parking distance limit while preserving Naolib vélo's normal account, subscription and API flow. Naolib vélo 3.6.1 only.",
) {
    compatibleWith(Compatibility(
        name = "Naolib vélo",
        packageName = "com.jcdecaux.vls.nantes",
        apkFileType = ApkFileType.XAPK,
        targets = listOf(AppTarget(version = "3.6.1")),
    ))

    execute {
        val distanceCheck = Fingerprint(
            definingClass = DISTANCE_CHECK,
            name = "a",
            returnType = "Lwt/e;",
            parameters = listOf(LOCATION),
            filters = listOf(
                methodCall(
                    definingClass = PARK_REPOSITORY,
                    name = "c",
                    parameters = listOf("Ljava/util/UUID;", "Ljava/util/UUID;"),
                    returnType = "Lwt/a;",
                    opcodes = listOf(Opcode.INVOKE_INTERFACE),
                ),
            ),
        ).matchAll(1..1).single()

        val method = distanceCheck.method
        val instructions = method.implementation!!.instructions.toList()
        val expectedOpcodes = listOf(
            Opcode.CONST_STRING, Opcode.INVOKE_STATIC, Opcode.IGET_OBJECT,
            Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT, Opcode.IF_NEZ, Opcode.IGET_OBJECT,
            Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT, Opcode.SGET_OBJECT, Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT, Opcode.IGET_OBJECT, Opcode.INVOKE_STATIC,
            Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_INTERFACE, Opcode.MOVE_RESULT_OBJECT,
            Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT,
            Opcode.INT_TO_FLOAT, Opcode.CMPL_FLOAT, Opcode.IF_GTZ, Opcode.IGET_OBJECT,
            Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT_OBJECT, Opcode.IGET_OBJECT,
            Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT, Opcode.IGET_OBJECT,
            Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT, Opcode.INVOKE_INTERFACE, Opcode.MOVE_RESULT_OBJECT,
            Opcode.RETURN_OBJECT, Opcode.NEW_INSTANCE, Opcode.INVOKE_DIRECT, Opcode.THROW,
        )
        check(instructions.map { it.opcode } == expectedOpcodes) {
            "Unexpected Naolib parking-distance method instruction sequence"
        }

        val repositoryCall = instructions[41] as? ReferenceInstruction
        val repositoryReference = repositoryCall?.reference as? MethodReference
        check(repositoryReference?.definingClass == PARK_REPOSITORY &&
            repositoryReference.name == "c" &&
            repositoryReference.parameterTypes.map { it.toString() } ==
                listOf("Ljava/util/UUID;", "Ljava/util/UUID;") &&
            repositoryReference.returnType == "Lwt/a;") {
            "Unexpected Naolib parking repository call"
        }
        val proximityAllocation = instructions[44] as? ReferenceInstruction
        check((proximityAllocation?.reference as? TypeReference)?.type == PROXIMITY_EXCEPTION) {
            "Unexpected Naolib proximity exception type"
        }
        val proximityConstructor = instructions[45] as? ReferenceInstruction
        val proximityConstructorReference = proximityConstructor?.reference as? MethodReference
        check(proximityConstructorReference?.definingClass == PROXIMITY_EXCEPTION &&
            proximityConstructorReference.name == "<init>" &&
            proximityConstructorReference.parameterTypes.isEmpty() &&
            proximityConstructorReference.returnType == "V") {
            "Unexpected Naolib proximity exception constructor"
        }

        // Keep the normal reservation/API path and bypass only the distance rejection.
        method.replaceInstruction(29, "nop")
    }
}
