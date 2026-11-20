package dev.blockside.core;

/** A completed, usable building. */
public final class Facility {
    public String id;
    public String catalogId;
    public String name;
    public String category;
    public String service;
    public int capacity;
    public Pos anchor;
    public Pos entrance;
    public Box bounds;
    public boolean open = true;
    public long builtAt;
}
