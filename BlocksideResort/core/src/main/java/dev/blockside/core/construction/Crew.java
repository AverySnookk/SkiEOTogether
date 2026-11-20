package dev.blockside.core.construction;

public final class Crew {
    public String id;
    public String name;
    public String jobId; // null = idle
    public long hiredAt;
    public boolean idle() { return jobId == null; }
}
