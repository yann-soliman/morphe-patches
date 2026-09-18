package io.github.yannsoliman.patches.naolib

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val OPEN_DATA_REPOSITORY = "Lhi/y;"
private const val PARK_NETWORK_MAPPER = "Lhi/y\$d;"
private const val NON_EMPTY_LIST_PREDICATE = "Lyi/b\$a\$b;"
private const val RX_OBSERVABLE = "Lwt/o;"
private const val RX_FUNCTION = "Lzt/i;"
private const val RX_PREDICATE = "Lzt/k;"
private const val PARK_API = "Lug/l;"
private const val PARK_CACHE = "Lli/a0;"

private fun ReferenceInstruction.methodReference(): MethodReference? = reference as? MethodReference

@Suppress("unused")
val naolibParkingCachePatch = bytecodePatch(
    name = "Naolib vélo: keep parking list",
    description = "Keep cached parking markers when the parking API returns an empty list. Naolib vélo 3.6.1 only.",
) {
    compatibleWith(Compatibility(
        name = "Naolib vélo",
        packageName = "com.jcdecaux.vls.nantes",
        apkFileType = ApkFileType.XAPK,
        targets = listOf(AppTarget(version = "3.6.1")),
    ))

    execute {
        // This app-provided singleton implements Lzt/k and accepts exactly non-empty
        // Lists. Verify its shape before reusing it; it is not changed by this patch.
        val predicate = Fingerprint(
            definingClass = NON_EMPTY_LIST_PREDICATE,
            name = "a",
            returnType = "Z",
            parameters = listOf("Ljava/util/List;"),
            filters = listOf(
                methodCall(
                    definingClass = "Ljava/util/Collection;",
                    name = "isEmpty",
                    returnType = "Z",
                    opcodes = listOf(Opcode.INVOKE_INTERFACE),
                ),
            ),
        ).matchAll(1..1).single()
        check(predicate.classDef.interfaces == listOf(RX_PREDICATE)) {
            "Unexpected Naolib non-empty list predicate interface"
        }
        val predicateInstructions = predicate.method.implementation!!.instructions.toList()
        check(predicateInstructions.map { it.opcode } == listOf(
            Opcode.CONST_STRING, Opcode.INVOKE_STATIC, Opcode.INVOKE_INTERFACE,
            Opcode.MOVE_RESULT, Opcode.XOR_INT_LIT8, Opcode.RETURN,
        )) { "Unexpected Naolib non-empty list predicate body" }
        check((predicateInstructions[4] as NarrowLiteralInstruction).narrowLiteral == 1) {
            "Unexpected Naolib list predicate inversion"
        }
        val singleton = predicate.classDef.fields.single {
            it.name == "a" && it.type == NON_EMPTY_LIST_PREDICATE
        }
        check(AccessFlags.PUBLIC.isSet(singleton.accessFlags) &&
            AccessFlags.STATIC.isSet(singleton.accessFlags)) {
            "Naolib list predicate singleton is not public static"
        }
        Fingerprint(
            definingClass = NON_EMPTY_LIST_PREDICATE,
            name = "<clinit>",
            returnType = "V",
            parameters = emptyList(),
            filters = listOf(
                fieldAccess(
                    definingClass = NON_EMPTY_LIST_PREDICATE,
                    name = "a",
                    type = NON_EMPTY_LIST_PREDICATE,
                    opcode = Opcode.SPUT_OBJECT,
                ),
            ),
        ).matchAll(1..1).single()

        val getAllParks = Fingerprint(
            definingClass = OPEN_DATA_REPOSITORY,
            name = "d",
            returnType = RX_OBSERVABLE,
            parameters = listOf("J", "Z"),
            filters = listOf(
                methodCall(
                    definingClass = PARK_CACHE,
                    name = "getAll",
                    returnType = "Lwt/u;",
                    opcodes = listOf(Opcode.INVOKE_INTERFACE),
                ),
                methodCall(
                    definingClass = PARK_API,
                    name = "a",
                    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
                    returnType = RX_OBSERVABLE,
                    opcodes = listOf(Opcode.INVOKE_INTERFACE),
                ),
                methodCall(
                    definingClass = PARK_NETWORK_MAPPER,
                    name = "<init>",
                    parameters = listOf("Lzg/l;"),
                    returnType = "V",
                    opcodes = listOf(Opcode.INVOKE_DIRECT),
                ),
            ),
        ).matchAll(1..1).single()

        val mapperConstruction = getAllParks.instructionMatches.single { match ->
            val reference = (match.instruction as? ReferenceInstruction)?.methodReference()
            reference?.definingClass == PARK_NETWORK_MAPPER && reference.name == "<init>"
        }
        val method = getAllParks.method
        val instructions = method.implementation!!.instructions.toList()
        val mapperIndex = mapperConstruction.index
        check(mapperIndex == 52 && instructions.size == 104) {
            "Unexpected Naolib park network mapper position"
        }
        check(instructions.subList(mapperIndex - 1, mapperIndex + 4).map { it.opcode } == listOf(
            Opcode.NEW_INSTANCE, Opcode.INVOKE_DIRECT, Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT, Opcode.IGET_OBJECT,
        )) { "Unexpected Naolib park network mapper sequence" }
        val mapReference = (instructions[mapperIndex + 1] as? ReferenceInstruction)?.methodReference()
        check(mapReference?.definingClass == RX_OBSERVABLE &&
            mapReference.name == "Y" &&
            mapReference.parameterTypes.map { it.toString() } == listOf(RX_FUNCTION) &&
            mapReference.returnType == RX_OBSERVABLE) {
            "Unexpected Naolib park network map call"
        }
        val cacheWriteReference = (instructions[mapperIndex + 10] as? ReferenceInstruction)?.methodReference()
        check(cacheWriteReference?.definingClass == RX_OBSERVABLE &&
            cacheWriteReference.name == "A" &&
            cacheWriteReference.parameterTypes.map { it.toString() } == listOf("Lzt/e;") &&
            cacheWriteReference.returnType == RX_OBSERVABLE) {
            "Unexpected Naolib park cache write call"
        }

        val mapCall = instructions[mapperIndex + 1] as FiveRegisterInstruction
        check(mapCall.registerCount == 2 && mapCall.registerC == 0 && mapCall.registerD == 4 &&
            (instructions[mapperIndex + 2] as OneRegisterInstruction).registerA == 0) {
            "Unexpected Naolib park observable registers"
        }
        // The reused predicate is package-private in the original app. Make the
        // class accessible to Lhi/y; before introducing this cross-package use.
        predicate.classDef.setAccessFlags(predicate.classDef.accessFlags or AccessFlags.PUBLIC.value)

        // H is RxJava Observable.filter. Filtering the remote source before its
        // scheduler and doOnNext cache write leaves cached data and the UI intact
        // when that source emits an empty park list.
        method.addInstructions(
            mapperIndex + 3,
            """
                sget-object v4, $NON_EMPTY_LIST_PREDICATE->a:$NON_EMPTY_LIST_PREDICATE
                invoke-virtual {v0, v4}, $RX_OBSERVABLE->H($RX_PREDICATE)$RX_OBSERVABLE
                move-result-object v0
            """.trimIndent(),
        )
    }
}
