package dev.blockside.core.economy;

/** Ledger transaction categories. Only GUEST_* kinds count as guest revenue for XP. */
public enum TxKind {
    GUEST_TICKET(true), GUEST_RENTAL(true), GUEST_LESSON(true), GUEST_FOOD(true), GUEST_LODGING(true),
    PROJECT_PURCHASE(false), PROJECT_REFUND(false), CREW_LABOR(false), CREW_WAGES(false),
    OPERATING_POWER(false), OPERATING_WATER(false), OPERATING_OTHER(false), GRANT(false), ADMIN(false);

    public final boolean guestRevenue;

    TxKind(boolean guestRevenue) { this.guestRevenue = guestRevenue; }
}
