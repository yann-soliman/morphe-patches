package com.jcdecaux.vls.app.map.model;

public class MapItem implements java.io.Serializable {
    public static class Park extends MapItem {
        private final Origin origin;
        public Park() { this(1, "Parking test"); }
        public Park(int id, String title) { origin = new Origin(id, title); }
        public Park(Origin origin) { this.origin = origin; }
        public com.google.android.gms.maps.model.LatLng getPosition() { return origin.position; }
        public Origin e() { return origin; }
        public String getTitle() { return origin.title; }
    }
    public static class Origin implements java.io.Serializable {
        final int id;
        final String title;
        public final com.google.android.gms.maps.model.LatLng position = new com.google.android.gms.maps.model.LatLng(47.2, -1.55);
        public Status status = Status.OPEN;
        public Availability availability = new Availability(10, 3);
        public enum Status { OPEN, CLOSED, INACTIVE }
        public Status g() { return status; }
        public Availability a() { return availability; }
        Origin(int id, String title) { this.id = id; this.title = title; }
        public int f() { return id; }
    }
    public static class Availability implements java.io.Serializable {
        private final Integer total, free;
        public Availability(Integer total, Integer free) { this.total = total; this.free = free; }
        public Integer a() { return free; }
        public Integer b() { return total; }
    }
}
