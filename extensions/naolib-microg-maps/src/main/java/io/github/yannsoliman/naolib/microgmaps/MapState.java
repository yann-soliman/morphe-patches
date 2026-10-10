package io.github.yannsoliman.naolib.microgmaps;

import android.content.Context;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import org.json.JSONObject;

/** Remember the camera without replacing Naolib's existing map callbacks. */
public final class MapState {
    static final String PREFERENCES = "naolib_map_position";
    private static final WeakHashMap<Object, Context> contexts = new WeakHashMap<>();
    private MapState() {}

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    public static void initialize(Object wrapper) {
        try {
            Context context = ((Context) field(wrapper, "a")).getApplicationContext();
            Object map = field(wrapper, "b");
            synchronized (contexts) { contexts.put(map, context); }
            String saved = context.getSharedPreferences(PREFERENCES, 0).getString("camera", null);
            if (saved == null) return;
            JSONObject state = new JSONObject(saved);
            if (!valid(state)) return;
            ClassLoader loader = context.getClassLoader();
            Class<?> latLng = loader.loadClass("com.google.android.gms.maps.model.LatLng");
            Object target = latLng.getConstructor(double.class, double.class)
                .newInstance(state.getDouble("latitude"), state.getDouble("longitude"));
            Class<?> camera = loader.loadClass("com.google.android.gms.maps.model.CameraPosition");
            Object position = camera.getConstructor(latLng, float.class, float.class, float.class)
                .newInstance(target, (float) state.getDouble("zoom"), (float) state.getDouble("tilt"), (float) state.getDouble("bearing"));
            Method restore = wrapper.getClass().getDeclaredMethod("H", camera);
            restore.setAccessible(true);
            restore.invoke(wrapper, position);
        } catch (Exception | LinkageError failure) {
            Log.w("NaolibMicrogMaps", "Map position restore unavailable: " + failure.getClass().getSimpleName());
        }
    }

    public static void onCameraIdle(Object clusterManager) {
        try {
            Object map = field(clusterManager, "f");
            Context context;
            synchronized (contexts) { context = contexts.get(map); }
            if (context == null) return;
            Object camera = map.getClass().getMethod("f").invoke(map);
            Object target = camera.getClass().getField("a").get(camera);
            JSONObject state = new JSONObject();
            state.put("latitude", target.getClass().getField("a").getDouble(target));
            state.put("longitude", target.getClass().getField("b").getDouble(target));
            state.put("zoom", camera.getClass().getField("b").getFloat(camera));
            state.put("tilt", camera.getClass().getField("c").getFloat(camera));
            state.put("bearing", camera.getClass().getField("d").getFloat(camera));
            if (valid(state)) context.getSharedPreferences(PREFERENCES, 0).edit().putString("camera", state.toString()).apply();
        } catch (Exception | LinkageError failure) {
            Log.w("NaolibMicrogMaps", "Map position save unavailable: " + failure.getClass().getSimpleName());
        }
    }

    static boolean recenter(Object fragment, Object item) {
        try {
            // Naolib exposes V7 as a protected method on a base fragment.
            Method getter = null;
            for (Class<?> type = fragment.getClass(); type != null; type = type.getSuperclass()) {
                try { getter = type.getDeclaredMethod("V7"); break; }
                catch (NoSuchMethodException missing) { /* Continue through the host's hierarchy. */ }
            }
            if (getter == null) throw new NoSuchMethodException("V7");
            getter.setAccessible(true);
            Object wrapper = getter.invoke(fragment);
            Object target = item.getClass().getMethod("getPosition").invoke(item);
            wrapper.getClass().getMethod("w0", target.getClass(), Float.class, Float.class)
                .invoke(wrapper, target, Float.valueOf(16), null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Log.w("NaolibMicrogMaps", "Favorite recenter unavailable: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean valid(JSONObject state) {
        double lat = state.optDouble("latitude", Double.NaN), lng = state.optDouble("longitude", Double.NaN);
        double zoom = state.optDouble("zoom", Double.NaN), tilt = state.optDouble("tilt", Double.NaN);
        double bearing = state.optDouble("bearing", Double.NaN);
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
            && zoom >= 0 && zoom <= 24 && tilt >= 0 && tilt <= 90 && Double.isFinite(bearing);
    }
}
