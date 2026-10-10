package io.github.yannsoliman.naolib.microgmaps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import com.jcdecaux.vls.app.map.model.MapItem;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, manifest = Config.NONE)
public class MicrogMapsRuntimeTest {
    @Before public void resetFavorites() throws Exception {
        org.robolectric.RuntimeEnvironment.getApplication()
            .getSharedPreferences(ParkingFavorites.PREFERENCES, 0).edit().clear().commit();
        org.robolectric.RuntimeEnvironment.getApplication()
            .getSharedPreferences(MapState.PREFERENCES, 0).edit().clear().commit();
        java.lang.reflect.Field latest = ParkingData.class.getDeclaredField("latest");
        latest.setAccessible(true);
        ((java.util.Map<?, ?>) latest.get(null)).clear();
    }

    @Test public void stylePreservesRoadRulesAndAddsOnlyPoiSuppression() throws Exception {
        String original = "[{\"featureType\":\"road\",\"stylers\":[{\"color\":\"#123456\"}]}]";
        JSONArray filtered = new JSONArray(MicrogMapsRuntime.hideBasemapPlaces(original));
        assertEquals(2, filtered.length());
        assertEquals(new JSONArray(original).getJSONObject(0).toString(), filtered.getJSONObject(0).toString());
        assertEquals("poi", filtered.getJSONObject(1).getString("featureType"));
        assertEquals("off", filtered.getJSONObject(1).getJSONArray("stylers").getJSONObject(0).getString("visibility"));
        assertEquals("invalid", MicrogMapsRuntime.hideBasemapPlaces("invalid"));
    }

    @Test public void missingMicrogFinishesHostAndOpensInstallScreen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertFalse(MicrogMapsRuntime.ensureAvailable(activity));
        assertTrue(activity.isFinishing());
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertEquals(MicrogRequiredActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test public void downloadRequiresButtonPressAndOpensOfficialPage() {
        MicrogRequiredActivity screen = Robolectric.buildActivity(MicrogRequiredActivity.class).setup().get();
        assertNull(shadowOf(screen).getNextStartedActivity());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        Intent next = shadowOf(screen).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, next.getAction());
        assertEquals(MicrogRequiredActivity.DOWNLOAD, next.getDataString());
        assertTrue(screen.isFinishing());
    }

    public static class FragmentBase {
        protected final Wrapper V7() { return ((Fragment) this).wrapper; }
    }
    public static class Fragment extends FragmentBase {
        final Activity activity;
        int detailsOpened;
        final android.widget.FrameLayout view;
        final Wrapper wrapper;
        Fragment(Activity activity) { this.activity = activity; view = activity == null ? null : new android.widget.FrameLayout(activity); wrapper = new Wrapper(activity); }
        public android.view.View getView() { return view; }
        public Activity getActivity() { return activity; }
        public void Q9(MapItem.Park park) {
            if (!MicrogMapsRuntime.showParkingPopup(this, park)) detailsOpened++;
        }
    }

    @Test public void parkingTapShowsNameAndOnlyDetailsButtonNavigates() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Fragment fragment = new Fragment(activity);
        MapItem.Park park = new MapItem.Park();
        fragment.Q9(park);
        assertEquals(0, fragment.detailsOpened);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        assertEquals("Parking test", shadowOf(dialog).getTitle().toString());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1, fragment.detailsOpened);
        assertFalse(dialog.isShowing());
        // The bypass must be cleared so subsequent taps show the popup again.
        fragment.Q9(park);
        assertEquals(1, fragment.detailsOpened);
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
    }

    @Test public void closingParkingPopupDoesNotOpenDetails() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Fragment fragment = new Fragment(activity);
        fragment.Q9(new MapItem.Park());
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(0, fragment.detailsOpened);
        assertFalse(dialog.isShowing());
        assertFalse(MicrogMapsRuntime.showParkingPopup(new Fragment(null), new MapItem.Park()));
    }
    @Test public void favoritesPersistByParkingIdAndCanBeRemoved() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        MapItem.Park park = new MapItem.Park(42, "Gare Sud");
        assertTrue(ParkingFavorites.toggle(activity, park));
        assertTrue(ParkingFavorites.contains(activity, new MapItem.Park(42, "Nouveau nom")));
        android.content.SharedPreferences prefs = activity.getSharedPreferences(ParkingFavorites.PREFERENCES, 0);
        org.json.JSONObject entry = new org.json.JSONObject(prefs.getString("park_42", ""));
        MapItem.Park restored = (MapItem.Park) ParkingFavorites.restore(activity, entry);
        assertEquals("Gare Sud", restored.getTitle());
        assertEquals(42, restored.e().f());
        assertTrue(ParkingFavorites.toggle(activity, restored));
        assertFalse(ParkingFavorites.contains(activity, park));
        assertEquals(0, prefs.getAll().size());
    }

    @Test public void favoriteButtonIsUniqueAndOnlyItsPressOpensList() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Fragment fragment = new Fragment(activity);
        ParkingFavorites.attach(fragment);
        ParkingFavorites.attach(fragment);
        assertEquals(1, fragment.view.getChildCount());
        android.view.ViewGroup overlay = fragment.view.findViewWithTag(ParkingFavorites.OVERLAY);
        assertEquals("Parkings favoris", overlay.getChildAt(0).getContentDescription().toString());
        overlay.getChildAt(0).performClick();
        assertEquals("Parkings favoris", shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString());
        ParkingFavorites.detach(fragment);
        assertEquals(0, fragment.view.getChildCount());
    }

    @Test public void favoriteListSelectionOpensPopupBeforeOriginalDetails() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Fragment fragment = new Fragment(activity);
        assertTrue(ParkingFavorites.toggle(activity, new MapItem.Park(10, "Gare")));
        ParkingFavorites.show(fragment);
        AlertDialog list = ShadowAlertDialog.getLatestAlertDialog();
        list.getListView().performItemClick(list.getListView().getChildAt(0), 0, 0);
        shadowOf(android.os.Looper.getMainLooper()).idle();
        AlertDialog popup = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Gare", shadowOf(popup).getTitle().toString());
        assertEquals(0, fragment.detailsOpened);
        assertEquals(47.2, fragment.wrapper.b.camera.a.a, 0.00001);
        assertEquals(16f, fragment.wrapper.b.camera.b, 0.0);
        assertEquals("Retirer des favoris", popup.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
        popup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1, fragment.detailsOpened);
    }

    public static class Map {
        com.google.android.gms.maps.model.CameraPosition camera = new com.google.android.gms.maps.model.CameraPosition(
            new com.google.android.gms.maps.model.LatLng(0, 0), 1, 0, 0);
        public com.google.android.gms.maps.model.CameraPosition f() { return camera; }
    }
    public static class Wrapper {
        private final android.content.Context a;
        private final Map b = new Map();
        boolean manual;
        Wrapper(android.content.Context context) { a = context; }
        private void H(com.google.android.gms.maps.model.CameraPosition camera) { b.camera = camera; manual = true; }
        public void w0(com.google.android.gms.maps.model.LatLng target, Float zoom, Float bearing) {
            b.camera = new com.google.android.gms.maps.model.CameraPosition(target, zoom, 0, b.camera.d); manual = true;
        }
    }
    public static class Cluster {
        private final Map f;
        Cluster(Map map) { f = map; }
    }

    @Test public void cameraIdlePersistsAndNextMapRestoresPositionAndZoom() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Wrapper first = new Wrapper(activity);
        MapState.initialize(first);
        assertFalse(first.manual);
        first.b.camera = new com.google.android.gms.maps.model.CameraPosition(
            new com.google.android.gms.maps.model.LatLng(47.25, -1.6), 17, 30, 80);
        MapState.onCameraIdle(new Cluster(first.b));
        Wrapper reopened = new Wrapper(activity);
        MapState.initialize(reopened);
        assertEquals(47.25, reopened.b.camera.a.a, 0.000001);
        assertEquals(-1.6, reopened.b.camera.a.b, 0.000001);
        assertEquals(17, reopened.b.camera.b, 0);
        assertEquals(30, reopened.b.camera.c, 0);
        assertEquals(80, reopened.b.camera.d, 0);
        assertTrue(reopened.manual);
        activity.getSharedPreferences(MapState.PREFERENCES, 0).edit().putString("camera", "{\"latitude\":999}").commit();
        Wrapper invalid = new Wrapper(activity);
        MapState.initialize(invalid);
        assertFalse(invalid.manual);
    }

    @Test public void availabilityUsesLatestFeedAndDoesNotPresentSavedCountsAsCurrent() {
        MapItem.Park saved = new MapItem.Park(42, "Ancien nom");
        assertTrue(ParkingData.message(saved, true).contains("non actualisée"));
        MapItem.Park updated = new MapItem.Park(42, "Nouveau nom");
        updated.e().availability = new MapItem.Availability(20, 0);
        ParkingData.observe(java.util.Collections.singletonList(updated.e()));
        MapItem.Park latest = (MapItem.Park) ParkingData.latestItem(saved);
        assertEquals("Nouveau nom", latest.getTitle());
        assertEquals(42, latest.e().f());
        String text = ParkingData.message(saved, true);
        assertTrue(text.contains("Parking ouvert"));
        assertTrue(text.contains("0 places libres sur 20"));
        assertTrue(text.contains("Données reçues à"));
        updated.e().status = MapItem.Origin.Status.CLOSED;
        updated.e().availability = new MapItem.Availability(20, null);
        assertTrue(ParkingData.message(updated, false).contains("Parking fermé"));
        assertTrue(ParkingData.message(updated, false).contains("Disponibilité non communiquée"));
        assertTrue(ParkingData.message(updated, false).contains("Capacité : 20 places"));
    }

}
