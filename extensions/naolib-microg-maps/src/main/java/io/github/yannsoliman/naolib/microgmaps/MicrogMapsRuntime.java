package io.github.yannsoliman.naolib.microgmaps;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.app.Activity;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;

/** Load the user's installed alternative renderer; no Google certificate hooks. */
public final class MicrogMapsRuntime {
    private static final String TAG = "NaolibMicrogMaps";
    private static final String PACKAGE = "app.revanced.android.gms";
    private static final String CREATOR = "com.google.android.gms.maps.internal.CreatorImpl";
    private static Context module;

    private MicrogMapsRuntime() {}

    public static boolean ensureAvailable(Activity activity) {
        try {
            load(activity);
            return true;
        } catch (RuntimeException | LinkageError unavailable) {
            activity.startActivity(new Intent(activity, MicrogRequiredActivity.class));
            activity.finish();
            return false;
        }
    }

    /** Preserve the application's style and append only a basemap POI filter. */
    public static String hideBasemapPlaces(String json) {
        try {
            JSONArray operations = new JSONArray(json);
            JSONObject hide = new JSONObject();
            hide.put("featureType", "poi");
            hide.put("stylers", new JSONArray().put(new JSONObject().put("visibility", "off")));
            operations.put(hide);
            return operations.toString();
        } catch (Exception invalidStyle) {
            Log.w(TAG, "Basemap POI filter skipped: invalid style JSON");
            return json;
        }
    }

    private static final ThreadLocal<Boolean> openingDetails = new ThreadLocal<>();

    /** Intercept the final parking navigation entry point, independent of renderer. */
    public static boolean showParkingPopup(Object fragment, Object item) {
        return showParkingPopup(fragment, item, false);
    }

    static boolean showParkingPopup(Object fragment, Object original, boolean savedFavorite) {
        final Object item = ParkingData.latestItem(original);
        if (Boolean.TRUE.equals(openingDetails.get())) return false;
        try {
            Activity activity = (Activity) fragment.getClass().getMethod("getActivity").invoke(fragment);
            if (activity == null || activity.isFinishing()) return false;
            String title = (String) item.getClass().getMethod("getTitle").invoke(item);
            if (title == null || title.trim().isEmpty()) title = "Véloparc";
            new android.app.AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(ParkingData.message(item, savedFavorite))
                .setPositiveButton("Voir le parking", (dialog, which) -> openParkingDetails(fragment, item))
                .setNegativeButton("Fermer", null)
                .setNeutralButton(ParkingFavorites.contains(activity, item) ? "Retirer des favoris" : "Ajouter aux favoris",
                    (dialog, which) -> ParkingFavorites.toggle(activity, item))
                .show();
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Log.w(TAG, "Parking popup unavailable: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private static void openParkingDetails(Object fragment, Object item) {
        openingDetails.set(true);
        try {
            java.lang.reflect.Method method = fragment.getClass().getDeclaredMethod("Q9", item.getClass());
            method.setAccessible(true);
            method.invoke(fragment, item);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Log.w(TAG, "Parking details unavailable: " + failure.getClass().getSimpleName());
        } finally {
            openingDetails.remove();
        }
    }

    public static synchronized Context load(Context caller) {
        if (module != null) return module;
        try {
            PackageInfo info = caller.getPackageManager().getPackageInfo(PACKAGE, 0);
            Context candidate = caller.createPackageContext(PACKAGE,
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Class<?> creator = candidate.getClassLoader().loadClass(CREATOR);
            if (!android.os.IBinder.class.isAssignableFrom(creator)) {
                throw new IllegalStateException("microG Maps factory is not a Binder");
            }
            // Use Morphe microG’s bundled MapLibre engine.
            candidate.getClassLoader().loadClass("org.microg.gms.maps.mapbox.MapboxCreator");
            module = candidate;
            Log.i(TAG, "microG Maps module selected; package=" + PACKAGE + "; version=" + info.versionName);
            return module;
        } catch (PackageManager.NameNotFoundException | ClassNotFoundException | RuntimeException | LinkageError failure) {
            Log.e(TAG, "microG Maps module unavailable: " + failure.getClass().getSimpleName());
            // Explicit failure: never silently fall back to Google's unauthorized renderer.
            throw new IllegalStateException("Naolib requires MicroG-RE with its Maps renderer", failure);
        }
    }
}
