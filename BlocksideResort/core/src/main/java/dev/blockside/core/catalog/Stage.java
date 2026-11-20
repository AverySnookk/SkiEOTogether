package dev.blockside.core.catalog;

/** Visible construction stages, executed in this order. */
public enum Stage {
    SITE_PREP("Site prep"), FOUNDATION("Foundation"), STRUCTURE("Structure"), EQUIPMENT("Equipment install");
    public final String label;
    Stage(String label) { this.label = label; }
}
