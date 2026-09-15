package io.github.yannsoliman.patches.meteoconsult

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode

private const val ADVERTISING_MANAGER =
    "Lcom/lachainemeteo/advertisingmanager/AdvertisingManager;"
private const val ADVERTISING_SPACE_ID =
    "Lcom/lachainemeteo/advertisingmanager/AdvertisingSpaceId;"
private const val AD = "Lcom/lachainemeteo/advertisingmanager/models/Ad;"

@Suppress("unused")
val meteoConsultAdsPatch = bytecodePatch(
    name = "Meteo Consult: remove advertisements",
    description = "Disable banner, video and interstitial advertisements. Meteo Consult 1.1.4 only."
) {
    compatibleWith(Compatibility(
        name = "Meteo Consult",
        packageName = "com.meteoconsult.androidapp",
        apkFileType = ApkFileType.XAPK,
        targets = listOf(AppTarget(version = "1.1.4"))
    ))
    execute {
        val getAd = Fingerprint(
            definingClass = ADVERTISING_MANAGER,
            name = "getAdForSpace",
            returnType = AD,
            parameters = listOf(ADVERTISING_SPACE_ID),
        ).matchAll(1..1).single().method

        val getAdInstructions = getAd.implementation!!.instructions.toList()
        check(getAdInstructions.map { it.opcode } == listOf(
            Opcode.INVOKE_VIRTUAL,
            Opcode.IGET_OBJECT,
            Opcode.INVOKE_INTERFACE,
            Opcode.MOVE_RESULT_OBJECT,
            Opcode.CHECK_CAST,
            Opcode.RETURN_OBJECT,
        )) { "Unexpected AdvertisingManager.getAdForSpace layout" }

        val getCount = Fingerprint(
            definingClass = ADVERTISING_MANAGER,
            name = "getCountProviderForSpace",
            returnType = "I",
            parameters = listOf(ADVERTISING_SPACE_ID),
        ).matchAll(1..1).single().method

        val getCountInstructions = getCount.implementation!!.instructions.toList()
        check(getCountInstructions.map { it.opcode } == listOf(
            Opcode.INVOKE_VIRTUAL,
            Opcode.IGET_OBJECT,
            Opcode.INVOKE_INTERFACE,
            Opcode.MOVE_RESULT_OBJECT,
            Opcode.CHECK_CAST,
            Opcode.IF_EQZ,
            Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT,
            Opcode.IF_EQZ,
            Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT,
            Opcode.RETURN,
            Opcode.CONST_4,
            Opcode.RETURN,
        )) { "Unexpected AdvertisingManager.getCountProviderForSpace layout" }

        // The manager remains initialized, but reports no ad inventory to every
        // view. This lets the existing error/empty paths remove the ad view and
        // avoids touching the SDK initialization or unrelated network requests.
        getAd.removeInstructions(0, getAdInstructions.size)
        getAd.addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
        getCount.removeInstructions(0, getCountInstructions.size)
        getCount.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
    }
}
