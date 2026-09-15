package io.github.yannsoliman.patches.marine

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.*
import com.android.tools.smali.dexlib2.AccessFlags

private const val FORECASTS = "Lcom/lachainemeteo/marine/data/network/models/forecasts/"

internal object BulletinRestrictionsFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("BulletinRestrictions(isBulletinRestricted="))
)

internal object BulletinRestrictionsConstructorFingerprint : Fingerprint(
    classFingerprint = BulletinRestrictionsFingerprint,
    name = "<init>",
    returnType = "V",
    parameters = listOf("Z", "Z")
)

internal object HourlyForecastTransformFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Ljava/util/LinkedHashMap;",
    parameters = listOf(
        "${FORECASTS}hourly/ForecastsHourlyContent;",
        "${FORECASTS}LegendContent;",
        "Z",
        "Lcom/lachainemeteo/marine/data/network/models/ReferenceContent;"
    ),
    filters = listOf(
        methodCall(definingClass = "${FORECASTS}Display;", name = "getHours"),
        methodCall(definingClass = "${FORECASTS}hourly/ForecastsHourlyContent;", name = "getForecasts")
    )
)

@Suppress("unused")
val marineForecastScreenPatch = bytecodePatch(
    name = "Marine Weather: remove forecast subscription screen",
    description = "Remove the local hourly bulletin limit and its subscription screen. Only displays data returned by the server. Marine Weather 7.1.3 only."
) {
    compatibleWith(Compatibility(
        name = "Marine Weather",
        packageName = "com.lachainemeteo.marine.androidapp",
        apkFileType = ApkFileType.APK,
        targets = listOf(AppTarget(version = "7.1.3"))
    ))
    execute {
        // Resolve every target before changing anything. Ambiguous APKs must fail.
        BulletinRestrictionsFingerprint.matchAll(1..1)
        val restrictions = BulletinRestrictionsConstructorFingerprint.matchAll(1..1).single().method
        val hourly = HourlyForecastTransformFingerprint.matchAll(1..1).single().method
        check(restrictions.implementation!!.registerCount <= 256)
        check(hourly.implementation!!.registerCount <= 256)

        // Instance constructor: p1 is isBulletinRestricted; p2 is the comparator.
        restrictions.addInstruction(0, "const/16 p1, 0x0")
        // Static mapper: p2 only controls truncation of the received hourly list.
        // No account, billing, request parameter or server response is altered.
        hourly.addInstruction(0, "const/16 p2, 0x1")
    }
}
