package com.google.android.gms.maps.model;

public class CameraPosition {
    public final LatLng a;
    public final float b, c, d;
    public CameraPosition(LatLng target, float zoom, float tilt, float bearing) {
        a = target; b = zoom; c = tilt; d = bearing;
    }
}
