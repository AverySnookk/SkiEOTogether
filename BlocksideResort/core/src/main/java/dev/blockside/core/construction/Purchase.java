package dev.blockside.core.construction;

/** A bought project. The kit item carries {@link #id}; each purchase can be placed or refunded once. */
public final class Purchase {
    public enum State { KIT, PLACED, REFUNDED }
    public String id;
    public String catalogId;
    public String buyer;
    public long price;
    public long boughtAt;
    public State state = State.KIT;
    public String jobId;
}
