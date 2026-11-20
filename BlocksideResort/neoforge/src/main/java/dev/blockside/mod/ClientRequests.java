package dev.blockside.mod;

/** Requests raised by common item code and consumed by the client tick (keeps client classes off servers). */
public final class ClientRequests {
    public static volatile boolean toggleSkis;
    public static volatile boolean openDashboard;
    /** Latest resort snapshot JSON received from the server. */
    public static volatile String snapshotJson;
    private ClientRequests() {}
}
