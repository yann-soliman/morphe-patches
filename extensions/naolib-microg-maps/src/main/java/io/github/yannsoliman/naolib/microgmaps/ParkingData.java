package io.github.yannsoliman.naolib.microgmaps;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import android.util.Log;

/** Observe the parking list supplied by Naolib, without making new API requests. */
public final class ParkingData {
    private static final HashMap<Integer, Snapshot> latest = new HashMap<>();
    private static final class Snapshot {
        final Object park;
        final long received;
        Snapshot(Object park, long received) { this.park = park; this.received = received; }
    }
    private ParkingData() {}

    public static synchronized void observe(List<?> parks) {
        if (parks == null) return;
        long now = System.currentTimeMillis();
        for (Object park : parks) {
            try {
                int id = ((Number) park.getClass().getMethod("f").invoke(park)).intValue();
                latest.put(id, new Snapshot(park, now));
            } catch (ReflectiveOperationException | RuntimeException failure) {
                Log.w("NaolibMicrogMaps", "Parking data unavailable: " + failure.getClass().getSimpleName());
            }
        }
    }

    private static synchronized Snapshot snapshot(Object item) throws ReflectiveOperationException {
        Object park = item.getClass().getMethod("e").invoke(item);
        int id = ((Number) park.getClass().getMethod("f").invoke(park)).intValue();
        return latest.get(id);
    }

    static Object latestItem(Object item) {
        try {
            Snapshot snapshot = snapshot(item);
            if (snapshot != null) return item.getClass().getConstructor(snapshot.park.getClass()).newInstance(snapshot.park);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.w("NaolibMicrogMaps", "Updated parking unavailable: " + failure.getClass().getSimpleName());
        }
        return item;
    }

    static String message(Object item, boolean savedFavorite) {
        try {
            Snapshot snapshot = snapshot(item);
            if (savedFavorite && snapshot == null) return "Disponibilité non actualisée. Ouvrez les détails du parking pour consulter ses informations.";
            Object park = snapshot == null ? item.getClass().getMethod("e").invoke(item) : snapshot.park;
            Object availability = park.getClass().getMethod("a").invoke(park);
            Integer free = availability == null ? null : (Integer) availability.getClass().getMethod("a").invoke(availability);
            Integer total = availability == null ? null : (Integer) availability.getClass().getMethod("b").invoke(availability);
            StringBuilder text = new StringBuilder();
            Object status = park.getClass().getMethod("g").invoke(park);
            String name = status instanceof Enum ? ((Enum<?>) status).name() : "";
            if (name.equals("OPEN")) text.append("Parking ouvert\n");
            if (name.equals("CLOSED")) text.append("Parking fermé\n");
            if (name.equals("INACTIVE")) text.append("Parking hors service\n");
            if (free != null && free >= 0) {
                text.append(free).append(free == 1 ? " place libre" : " places libres");
                if (total != null && total >= free) text.append(" sur ").append(total);
            } else {
                text.append("Disponibilité non communiquée");
                if (total != null && total >= 0) text.append("\nCapacité : ").append(total).append(" places");
            }
            if (snapshot != null) text.append("\nDonnées reçues à ")
                .append(new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date(snapshot.received)));
            return text.toString();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return "Disponibilité non communiquée";
        }
    }
}
