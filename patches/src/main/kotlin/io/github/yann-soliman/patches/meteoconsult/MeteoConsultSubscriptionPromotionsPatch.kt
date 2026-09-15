package io.github.yannsoliman.patches.meteoconsult

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val CONFIGURATION_CONTENT =
    "Lcom/meteoconsult/androidapp/network/model/configuration/ConfigurationContent;"
private const val PROMOTION_VIEW_MODEL = "Lkm/q;"

@Suppress("unused")
val meteoConsultSubscriptionPromotionsPatch = bytecodePatch(
    name = "Meteo Consult: disable subscription promotions",
    description = "Disable automatic subscription paywall promotions while keeping voluntary account and subscription screens available. Meteo Consult 1.1.4 only."
) {
    compatibleWith(Compatibility(
        name = "Meteo Consult",
        packageName = "com.meteoconsult.androidapp",
        apkFileType = ApkFileType.XAPK,
        targets = listOf(AppTarget(version = "1.1.4"))
    ))
    execute {
        val configuration = Fingerprint(
            definingClass = CONFIGURATION_CONTENT,
            name = "isPopupPaywallActive",
            returnType = "Z",
            parameters = emptyList(),
        ).matchAll(1..1).single().method
        val configurationInstructions = configuration.implementation!!.instructions.toList()
        check(configurationInstructions.map { it.opcode } == listOf(
            Opcode.IGET_BOOLEAN,
            Opcode.RETURN,
        )) { "Unexpected ConfigurationContent.isPopupPaywallActive layout" }

        // The remote configuration controls whether the automatic popup branch
        // is eligible. Keep the value false even when the server enables it.
        configuration.removeInstructions(0, configurationInstructions.size)
        configuration.addInstructions(0, "const/4 v0, 0x0\nreturn v0")

        // Keep a second guard at the final placement decision. The enum field is
        // obfuscated, so identify it by its stable owner/type and the surrounding
        // return branch, then return null (the caller treats it as no promotion).
        val promotionDecision = Fingerprint(
            definingClass = PROMOTION_VIEW_MODEL,
            name = "b",
            returnType = "Ljava/lang/Enum;",
            parameters = listOf(PROMOTION_VIEW_MODEL, "Ltr/c;"),
            filters = listOf(
                fieldAccess(
                    definingClass = "Lsk/a;",
                    name = "e",
                    type = "Lsk/a;",
                    opcode = Opcode.SGET_OBJECT,
                )
            )
        ).matchAll(1..1).single()
        val promotionHit = promotionDecision.instructionMatches.single()
        val promotionInstructions = promotionDecision.method.implementation!!.instructions.toList()
        check(promotionHit.index > 0 &&
            promotionInstructions[promotionHit.index - 1].opcode == Opcode.IF_LT &&
            promotionHit.index + 1 < promotionInstructions.size &&
            promotionInstructions[promotionHit.index + 1].opcode == Opcode.RETURN_OBJECT
        ) { "Unexpected subscription promotion decision layout" }

        val register = (promotionHit.instruction as? OneRegisterInstruction)?.registerA
            ?: error("Subscription promotion field access has no destination register")
        promotionDecision.method.replaceInstruction(
            promotionHit.index,
            "const/4 v$register, 0x0",
        )
    }
}
