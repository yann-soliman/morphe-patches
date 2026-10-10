package io.github.yannsoliman.patches.naolib

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import org.w3c.dom.Element

private const val MICROG_PACKAGE = "app.revanced.android.gms"
private const val CONTEXT_TYPE = "Landroid/content/Context;"

internal val naolibMicrogVisibilityPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val queries = (0 until root.childNodes.length).map { root.childNodes.item(it) }
                .filterIsInstance<Element>().firstOrNull { it.tagName == "queries" }
                ?: manifest.createElement("queries").also { root.appendChild(it) }
            val namespace = "http://schemas.android.com/apk/res/android"
            val present = (0 until queries.childNodes.length).map { queries.childNodes.item(it) }
                .filterIsInstance<Element>().any {
                    it.tagName == "package" && it.getAttributeNS(namespace, "name") == MICROG_PACKAGE
                }
            if (!present) queries.appendChild(manifest.createElement("package").apply {
                setAttributeNS(namespace, "android:name", MICROG_PACKAGE)
            })
            val application = manifest.getElementsByTagName("application").item(0) as Element
            val screen = "io.github.yannsoliman.naolib.microgmaps.MicrogRequiredActivity"
            if ((0 until application.childNodes.length).map { application.childNodes.item(it) }
                .filterIsInstance<Element>().none { it.getAttributeNS(namespace, "name") == screen }) {
                application.appendChild(manifest.createElement("activity").apply {
                    setAttributeNS(namespace, "android:name", screen)
                    setAttributeNS(namespace, "android:exported", "false")
                    setAttributeNS(namespace, "android:theme", "@android:style/Theme.DeviceDefault.Dialog.Alert")
                })
            }
        }
    }
}

@Suppress("unused")
val naolibMicrogMapsPatch = bytecodePatch(
    name = "Naolib vélo : carte avec microG",
    description = "Affiche la carte avec Morphe microG et les informations des parkings. Permet d’enregistrer des parkings favoris sur le téléphone, de recentrer la carte sur un favori et de retrouver la dernière position et le dernier zoom. Propose le téléchargement de microG si nécessaire. Les services Google Play doivent rester installés. Compatible avec Naolib vélo 3.6.1.",
    default = true,
) {
    dependsOn(naolibPatchedInstallPatch, naolibMicrogVisibilityPatch)
    extendWith("extensions/naolib-microg-maps.mpe")
    compatibleWith(Compatibility(
        name = "Naolib vélo", packageName = "com.jcdecaux.vls.nantes",
        apkFileType = ApkFileType.XAPK, targets = listOf(AppTarget(version = "3.6.1")),
    ))

    execute {
        val loader = mutableClassDefBy("Lza/d0;")
        val contextMethod = loader.methods.single {
            it.name == "c" && it.parameterTypes == listOf(CONTEXT_TYPE,
                "Lcom/google/android/gms/maps/MapsInitializer\$Renderer;") && it.returnType == CONTEXT_TYPE
        }
        val originalInstructions = contextMethod.implementation!!.instructions.toList()
        check(originalInstructions.any {
            val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == "Lcom/google/android/gms/dynamite/DynamiteModule;" && ref.name == "c"
        }) { "Unexpected Naolib Maps module loader" }
        // Replace the entire method, including old Google Dynamite exception handlers.
        val replacement = MutableMethod(ImmutableMethod(contextMethod.definingClass,
            contextMethod.name, contextMethod.parameters, contextMethod.returnType,
            contextMethod.accessFlags, contextMethod.annotations, contextMethod.hiddenApiRestrictions,
            MutableMethodImplementation(2)))
        replacement.addInstructions(0, """
            invoke-static/range {p0 .. p0}, Lio/github/yannsoliman/naolib/microgmaps/MicrogMapsRuntime;->load($CONTEXT_TYPE)$CONTEXT_TYPE
            move-result-object p0
            return-object p0
        """.trimIndent())
        loader.methods.remove(contextMethod)
        loader.methods.add(replacement)

        // Google's renderer requires ICreator transaction 11 to load its native
        // library. MicroG's factory supports transactions 1..10 and loads its own
        // renderer libraries; that Google-only call must not be sent to MicroG.
        val initialize = loader.methods.single { it.name == "a" }
        val nativeCalls = initialize.implementation!!.instructions.withIndex().filter { (_, instruction) ->
            val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_INTERFACE && ref?.definingClass == "Lza/g0;" &&
                ref.name == "D0" && ref.parameterTypes == listOf("Loa/b;") && ref.returnType == "V"
        }
        check(nativeCalls.size == 1) { "Unexpected Maps Google native-loader call" }
        initialize.replaceInstruction(nativeCalls.single().index, "nop")

        val activity = mutableClassDefBy("Lcom/jcdecaux/vls/app/navigation/NavigationActivity;")
        val onCreate = activity.methods.single { it.name == "onCreate" && it.parameterTypes == listOf("Landroid/os/Bundle;") }
        val superIndex = onCreate.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_SUPER &&
                ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "onCreate"
        }
        check(superIndex >= 0) { "Navigation activity super.onCreate missing" }
        // v0 holds constant 3 here; restore it on the successful path rather than
        // clobbering any of the host's live register values.
        check(onCreate.implementation!!.registerCount == 4) { "Unexpected navigation registers" }
        val originalV0 = onCreate.implementation!!.instructions.take(superIndex).last {
            it is OneRegisterInstruction && it.registerA == 0
        }
        check(originalV0.opcode == Opcode.CONST_4 &&
            (originalV0 as NarrowLiteralInstruction).narrowLiteral == 3) {
            "Unexpected live navigation v0 value"
        }
        onCreate.addInstructions(superIndex + 1, """
            invoke-static/range {p0 .. p0}, Lio/github/yannsoliman/naolib/microgmaps/MicrogMapsRuntime;->ensureAvailable(Landroid/app/Activity;)Z
            move-result v0
            return-void
            const/4 v0, 0x3
        """.trimIndent())

        // Attach the branch to a label in the host implementation. A relative
        // offset assembled in a standalone snippet is not relocated on insertion.
        val navigationBody = onCreate.implementation!!
        val ready = navigationBody.newLabelForIndex(superIndex + 4)
        navigationBody.addInstruction(superIndex + 3, BuilderInstruction21t(Opcode.IF_NEZ, 0, ready))

        val style = mutableClassDefBy("Lcom/google/android/gms/maps/model/MapStyleOptions;").methods.single {
            it.name == "<init>" && it.parameterTypes == listOf("Ljava/lang/String;")
        }
        style.addInstructions(0, """
            invoke-static/range {p1 .. p1}, Lio/github/yannsoliman/naolib/microgmaps/MicrogMapsRuntime;->hideBasemapPlaces(Ljava/lang/String;)Ljava/lang/String;
            move-result-object p1
        """.trimIndent())

        val parkingDetails = mutableClassDefBy("Lcom/jcdecaux/vls/app/map/MapFragment;").methods.single {
            it.name == "Q9" && it.parameterTypes == listOf("Lcom/jcdecaux/vls/app/map/model/MapItem\$Park;") && it.returnType == "V"
        }
        check(parkingDetails.implementation!!.registerCount == 7) { "Unexpected parking navigation registers" }
        check(parkingDetails.implementation!!.instructions.first().opcode == Opcode.CONST_STRING) {
            "Unexpected parking navigation entry"
        }
        parkingDetails.addInstructions(0, """
            invoke-static/range {p0 .. p1}, Lio/github/yannsoliman/naolib/microgmaps/MicrogMapsRuntime;->showParkingPopup(Ljava/lang/Object;Ljava/lang/Object;)Z
            move-result v0
            return-void
        """.trimIndent())
        val detailsBody = parkingDetails.implementation!!
        val originalDetails = detailsBody.newLabelForIndex(3)
        detailsBody.addInstruction(2, BuilderInstruction21t(Opcode.IF_EQZ, 0, originalDetails))
        val mapFragment = mutableClassDefBy("Lcom/jcdecaux/vls/app/map/MapFragment;")
        listOf("onResume" to "attach", "onPause" to "detach").forEach { (lifecycle, action) ->
            val method = mapFragment.methods.single { it.name == lifecycle && it.parameterTypes.isEmpty() }
            check(method.implementation!!.instructions.first().opcode == Opcode.INVOKE_SUPER) {
                "Unexpected map fragment lifecycle entry"
            }
            method.addInstructions(1,
                "invoke-static/range {p0 .. p0}, Lio/github/yannsoliman/naolib/microgmaps/ParkingFavorites;->$action(Ljava/lang/Object;)V")
        }

        val parkingList = mapFragment.methods.single {
            it.name == "o" && it.parameterTypes == listOf("Ljava/util/List;") && it.returnType == "V"
        }
        parkingList.addInstructions(0,
            "invoke-static/range {p1 .. p1}, Lio/github/yannsoliman/naolib/microgmaps/ParkingData;->observe(Ljava/util/List;)V")

        val wrapper = mutableClassDefBy("Lcom/jcdecaux/vls/app/map/wrapper/DefaultMapWrapper;")
        val constructor = wrapper.methods.single { it.name == "<init>" }
        val last = constructor.implementation!!.instructions.toList().lastIndex
        check(constructor.implementation!!.instructions[last].opcode == Opcode.RETURN_VOID)
        constructor.addInstructions(last,
            "invoke-static/range {p0 .. p0}, Lio/github/yannsoliman/naolib/microgmaps/MapState;->initialize(Ljava/lang/Object;)V")
        val idle = mutableClassDefBy("Llf/c;").methods.single { it.name == "d" && it.parameterTypes.isEmpty() }
        check(idle.implementation!!.instructions.any {
            ((it as? ReferenceInstruction)?.reference as? MethodReference)?.returnType == "Lcom/google/android/gms/maps/model/CameraPosition;"
        }) { "Unexpected map camera-idle callback" }
        idle.addInstructions(0,
            "invoke-static/range {p0 .. p0}, Lio/github/yannsoliman/naolib/microgmaps/MapState;->onCameraIdle(Ljava/lang/Object;)V")

    }
}
