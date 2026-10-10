package io.github.yannsoliman.naolib.microgmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/** Parking bookmarks stored only in the host application's private preferences. */
public final class ParkingFavorites {
    static final String PREFERENCES = "naolib_parking_favorites";
    static final String OVERLAY = "naolib_parking_favorites_button";
    private ParkingFavorites() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String key(Object item) throws ReflectiveOperationException {
        Object park = item.getClass().getMethod("e").invoke(item);
        return "park_" + park.getClass().getMethod("f").invoke(park);
    }

    static boolean contains(Context context, Object item) {
        try { return preferences(context).contains(key(item)); }
        catch (ReflectiveOperationException | RuntimeException failure) { return false; }
    }

    static boolean toggle(Context context, Object item) {
        try {
            String key = key(item);
            SharedPreferences prefs = preferences(context);
            if (prefs.contains(key)) {
                prefs.edit().remove(key).apply();
                Toast.makeText(context, "Parking retiré des favoris", Toast.LENGTH_SHORT).show();
                return true;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream objects = new ObjectOutputStream(bytes)) { objects.writeObject(item); }
            JSONObject entry = new JSONObject();
            entry.put("title", item.getClass().getMethod("getTitle").invoke(item));
            entry.put("data", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
            prefs.edit().putString(key, entry.toString()).apply();
            Toast.makeText(context, "Parking ajouté aux favoris", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception failure) {
            Log.w("NaolibMicrogMaps", "Parking favorite unavailable: " + failure.getClass().getSimpleName());
            Toast.makeText(context, "Impossible de modifier ce favori", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    static Object restore(Context context, JSONObject entry) throws Exception {
        byte[] data = Base64.decode(entry.getString("data"), Base64.NO_WRAP);
        try (ObjectInputStream objects = new ObjectInputStream(new ByteArrayInputStream(data)) {
            @Override protected Class<?> resolveClass(ObjectStreamClass descriptor)
                    throws java.io.IOException, ClassNotFoundException {
                return Class.forName(descriptor.getName(), false, context.getClassLoader());
            }
        }) { return objects.readObject(); }
    }

    public static void show(Object fragment) {
        try {
            Activity activity = (Activity) fragment.getClass().getMethod("getActivity").invoke(fragment);
            if (activity == null || activity.isFinishing()) return;
            ArrayList<JSONObject> entries = new ArrayList<>();
            for (Map.Entry<String, ?> entry : preferences(activity).getAll().entrySet()) {
                if (!entry.getKey().startsWith("park_") || !(entry.getValue() instanceof String)) continue;
                try { entries.add(new JSONObject((String) entry.getValue())); }
                catch (Exception malformed) { /* Ignore unusable entries without discarding other favorites. */ }
            }
            Collator collator = Collator.getInstance(Locale.FRENCH);
            entries.sort((a, b) -> collator.compare(a.optString("title"), b.optString("title")));
            AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle("Parkings favoris")
                .setNegativeButton("Fermer", null);
            if (entries.isEmpty()) {
                dialog.setMessage("Aucun parking favori. Appuyez sur un parking sur la carte, puis sur « Ajouter aux favoris ».");
            } else {
                String[] titles = new String[entries.size()];
                for (int i = 0; i < titles.length; i++) titles[i] = entries.get(i).optString("title", "Véloparc");
                dialog.setItems(titles, (selected, index) -> {
                    try {
                        Object item = ParkingData.latestItem(restore(activity, entries.get(index)));
                        if (!MapState.recenter(fragment, item)) Toast.makeText(activity, "Impossible de recentrer la carte", Toast.LENGTH_SHORT).show();
                        MicrogMapsRuntime.showParkingPopup(fragment, item, true);
                    }
                    catch (Exception unavailable) {
                        Toast.makeText(activity, "Ce favori n’est plus disponible", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            dialog.show();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.w("NaolibMicrogMaps", "Parking favorites list unavailable: " + failure.getClass().getSimpleName());
        }
    }

    public static void attach(Object fragment) {
        try {
            Activity activity = (Activity) fragment.getClass().getMethod("getActivity").invoke(fragment);
            Object view = fragment.getClass().getMethod("getView").invoke(fragment);
            if (activity == null || !(view instanceof ViewGroup)) return;
            ViewGroup root = (ViewGroup) view;
            if (root.findViewWithTag(OVERLAY) != null) return;
            float density = activity.getResources().getDisplayMetrics().density;
            FrameLayout overlay = new FrameLayout(activity);
            overlay.setTag(OVERLAY);
            ImageButton star = new ImageButton(activity);
            star.setContentDescription("Parkings favoris");
            star.setImageResource(android.R.drawable.btn_star_big_on);
            star.setImageTintList(ColorStateList.valueOf(0xff64b500));
            int padding = Math.round(10 * density);
            star.setPadding(padding, padding, padding, padding);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(Color.WHITE);
            star.setBackground(background);
            star.setElevation(4 * density);
            star.setOnClickListener(clicked -> show(fragment));
            int size = Math.round(48 * density);
            FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
            layout.topMargin = Math.round(112 * density);
            layout.setMarginEnd(Math.round(16 * density));
            overlay.addView(star, layout);
            root.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.w("NaolibMicrogMaps", "Parking favorites button unavailable: " + failure.getClass().getSimpleName());
        }
    }

    public static void detach(Object fragment) {
        try {
            Object view = fragment.getClass().getMethod("getView").invoke(fragment);
            if (!(view instanceof ViewGroup)) return;
            View overlay = ((ViewGroup) view).findViewWithTag(OVERLAY);
            if (overlay != null && overlay.getParent() instanceof ViewGroup) ((ViewGroup) overlay.getParent()).removeView(overlay);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.w("NaolibMicrogMaps", "Parking favorites cleanup unavailable: " + failure.getClass().getSimpleName());
        }
    }
}
