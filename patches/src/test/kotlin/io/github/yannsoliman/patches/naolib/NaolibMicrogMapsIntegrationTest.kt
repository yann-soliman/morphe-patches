package io.github.yannsoliman.patches.naolib

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class NaolibMicrogMapsIntegrationTest {
    @Test fun originalApkLoadsMicrogRenderer() = verify(false)
    @Test fun microgAndDistancePatchesWorkTogether() = verify(true)

    @Test fun microgAndBothDistancePatchesWorkTogether() = verify(true, true)

    @Test fun allFourNaolibPatchesWorkTogether() = verify(true, true, true)

    private fun verify(includeDistance: Boolean, includeBicycle: Boolean = false, includeCache: Boolean = false) = runBlocking {
        val input = System.getenv("NAOLIB_TEST_APK")
        assumeTrue("Set NAOLIB_TEST_APK to original base.apk", input != null)
        val work = Files.createTempDirectory("naolib-microg-test").toFile()
        try {
            Patcher(PatcherConfig(apkFile = File(input!!), temporaryFilesPath = File(work, "patcher"),
                useArsclib = true)).use { patcher ->
                patcher += buildSet {
                    add(naolibMicrogMapsPatch)
                    if (includeDistance) add(naolibParkingDistancePatch)
                    if (includeBicycle) add(naolibBicycleDistancePatch)
                    if (includeCache) add(naolibParkingCachePatch)
                }
                patcher().collect { it.exception?.let { error -> throw AssertionError("microG patch failed", error) } }
                val output = patcher.get()
                val classes = output.dexFiles.flatMap { dex ->
                    val file = File(work, dex.name)
                    dex.stream.use { stream -> file.outputStream().use { stream.copyTo(it) } }
                    DexFileFactory.loadDexFile(file, Opcodes.getDefault()).classes
                }.associateBy { it.type }
                val loader = classes.getValue("Lza/d0;")
                val context = loader.methods.single { it.name == "c" }
                val instructions = context.implementation!!.instructions.toList()
                assertEquals(3, instructions.size)
                val call = (instructions[0] as ReferenceInstruction).reference as MethodReference
                assertEquals("Lio/github/yannsoliman/naolib/microgmaps/MicrogMapsRuntime;", call.definingClass)
                assertEquals("load", call.name)
                assertEquals(Opcode.RETURN_OBJECT, instructions.last().opcode)
                assertTrue(context.implementation!!.tryBlocks.isEmpty())
                val initialize = loader.methods.single { it.name == "a" }
                assertFalse(initialize.implementation!!.instructions.any {
                    val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                    ref?.definingClass == "Lza/g0;" && ref.name == "D0"
                })
                val startup = classes.getValue("Lcom/pairip/application/Application;").methods.single {
                    it.name == "attachBaseContext"
                }
                assertEquals(listOf(Opcode.NOP, Opcode.INVOKE_SUPER, Opcode.RETURN_VOID),
                    startup.implementation!!.instructions.map { it.opcode })
                assertFalse(classes.containsKey("Lio/github/yannsoliman/naolib/maps/MapsSignatureRuntime;"))
                val navigation = classes.getValue("Lcom/jcdecaux/vls/app/navigation/NavigationActivity;")
                    .methods.single { it.name == "onCreate" }
                val navigationInstructions = navigation.implementation!!.instructions.toList()
                val guard = navigationInstructions.indexOfFirst {
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "ensureAvailable"
                }
                assertTrue(guard > 0)
                assertEquals(Opcode.INVOKE_SUPER, navigationInstructions[guard - 1].opcode)
                assertEquals(Opcode.RETURN_VOID, navigationInstructions[guard + 3].opcode)
                // Check serialized DEX addresses, not just opcode ordering: ART
                // rejects branches into the middle of another instruction.
                val addresses = assertBranchTargets(navigationInstructions)
                val dependencyBranch = navigationInstructions[guard + 2] as OffsetInstruction
                assertEquals(addresses[guard + 4], addresses[guard + 2] + dependencyBranch.codeOffset)
                assertEquals(Opcode.CONST_4, navigationInstructions[guard + 4].opcode)
                val style = classes.getValue("Lcom/google/android/gms/maps/model/MapStyleOptions;")
                    .methods.single { it.name == "<init>" }
                val styleHook = (style.implementation!!.instructions.first() as ReferenceInstruction)
                    .reference as MethodReference
                assertEquals("hideBasemapPlaces", styleHook.name)
                val parkingDetails = classes.getValue("Lcom/jcdecaux/vls/app/map/MapFragment;")
                    .methods.single { it.name == "Q9" }
                val detailInstructions = parkingDetails.implementation!!.instructions.toList()
                val hook = (detailInstructions[0] as ReferenceInstruction).reference as MethodReference
                assertEquals("showParkingPopup", hook.name)
                val pcs = assertBranchTargets(detailInstructions)
                assertEquals(Opcode.IF_EQZ, detailInstructions[2].opcode)
                assertEquals(Opcode.RETURN_VOID, detailInstructions[3].opcode)
                assertEquals(pcs[4], pcs[2] + (detailInstructions[2] as OffsetInstruction).codeOffset)
                assertEquals(Opcode.CONST_STRING, detailInstructions[4].opcode)
                assertTrue("Original parking navigation removed", detailInstructions.any {
                    val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                    ref?.name == "startActivityForResult"
                })
                val fragment = classes.getValue("Lcom/jcdecaux/vls/app/map/MapFragment;")
                listOf("onResume" to "attach", "onPause" to "detach").forEach { (name, action) ->
                    val method = fragment.methods.single { it.name == name }
                    val code = method.implementation!!.instructions.toList()
                    assertBranchTargets(code)
                    assertEquals(Opcode.INVOKE_SUPER, code[0].opcode)
                    val ref = (code[1] as ReferenceInstruction).reference as MethodReference
                    assertEquals("Lio/github/yannsoliman/naolib/microgmaps/ParkingFavorites;", ref.definingClass)
                    assertEquals(action, ref.name)
                }
                assertTrue(classes.getValue("Lcom/jcdecaux/cyclocity/vls/core/domain/model/map/Searchable;")
                    .interfaces.contains("Ljava/io/Serializable;"))
                assertTrue(classes.getValue("Lcom/jcdecaux/vls/app/map/model/MapItem;")
                    .interfaces.contains("Ljava/io/Serializable;"))
                val feed = fragment.methods.single { it.name == "o" && it.parameterTypes == listOf("Ljava/util/List;") }
                assertEquals("observe", ((feed.implementation!!.instructions.first() as ReferenceInstruction).reference as MethodReference).name)
                assertBranchTargets(feed.implementation!!.instructions.toList())
                val wrapper = classes.getValue("Lcom/jcdecaux/vls/app/map/wrapper/DefaultMapWrapper;")
                val constructorCode = wrapper.methods.single { it.name == "<init>" }.implementation!!.instructions.toList()
                assertBranchTargets(constructorCode)
                assertEquals("initialize", ((constructorCode[constructorCode.lastIndex - 1] as ReferenceInstruction).reference as MethodReference).name)
                val idle = classes.getValue("Llf/c;").methods.single { it.name == "d" }
                assertEquals("onCameraIdle", ((idle.implementation!!.instructions.first() as ReferenceInstruction).reference as MethodReference).name)
                assertBranchTargets(idle.implementation!!.instructions.toList())
                val wrapperGetter = classes.getValue("Lon/a;").methods.single { it.name == "V7" }
                assertEquals("Lcom/jcdecaux/vls/app/map/wrapper/a;", wrapperGetter.returnType)
                assertTrue("Host getter visibility changed", wrapperGetter.accessFlags and 0x4 != 0)
                assertTrue(wrapper.methods.any { it.name == "w0" && it.parameterTypes == listOf(
                    "Lcom/google/android/gms/maps/model/LatLng;", "Ljava/lang/Float;", "Ljava/lang/Float;") })
                assertTrue(wrapper.methods.any { it.name == "H" && it.parameterTypes == listOf("Lcom/google/android/gms/maps/model/CameraPosition;") })
                assertTrue(classes.getValue("Lcom/jcdecaux/cyclocity/vls/core/domain/model/Availability;").methods.any {
                    it.name == "a" && it.returnType == "Ljava/lang/Integer;"
                })
                listOf("Lcl/g\$a;" to "t", "Lim/a;" to "a").forEachIndexed { index, (type, name) ->
                    val method = classes.getValue(type).methods.single { it.name == name }
                    val code = method.implementation!!.instructions.toList()
                    val branch = if (index == 0) 29 else 77
                    val success = if (index == 0) 37 else 79
                    val pcs = assertBranchTargets(code)
                    assertEquals(if (includeBicycle) Opcode.GOTO_32 else if (index == 0) Opcode.IF_LEZ else Opcode.IF_LTZ,
                        code[branch].opcode)
                    assertEquals(pcs[success], pcs[branch] + (code[branch] as OffsetInstruction).codeOffset)
                    assertTrue("Location/distance acquisition removed", code.any {
                        val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
                        ref?.definingClass == "Lim/b;" && ref.name == "b"
                    })
                }
                val resources = output.resources!!
                ZipFile(resources.resourcesApk).use { zip ->
                    val manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
                    assertTrue("microG package visibility missing from compiled manifest",
                        manifest.toString(Charsets.UTF_8).contains("app.revanced.android.gms") ||
                            manifest.toString(Charsets.UTF_16LE).contains("app.revanced.android.gms"))
                }
                System.getenv("NAOLIB_MICROG_MANIFEST_OUTPUT")?.let {
                    resources.resourcesApk!!.copyTo(File(it), overwrite = true)
                }
                val distance = classes.getValue("Lfk/c\$a\$a;").methods.single {
                    it.name == "a" && it.parameterTypes.firstOrNull() ==
                        "Lcom/jcdecaux/cyclocity/vls/core/domain/model/map/Location;"
                }
                assertEquals(!includeDistance,
                    distance.implementation!!.instructions.any { it.opcode == Opcode.IF_GTZ })
            }
        } finally { work.deleteRecursively() }
    }
    private fun assertBranchTargets(instructions: List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>): List<Int> {
        val addresses = instructions.runningFold(0) { pc, instruction -> pc + instruction.codeUnits }.dropLast(1)
        instructions.forEachIndexed { index, instruction ->
            if (instruction is OffsetInstruction) {
                assertTrue("Invalid DEX branch target at ${addresses[index]}",
                    addresses[index] + instruction.codeOffset in addresses)
            }
        }
        return addresses
    }

}
