package dev.blockside.core.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Spendable resort cash. Server-authoritative; all mutation goes through {@link #trySpend} /
 * {@link #earn} so that totals per category stay consistent with the balance.
 */
public final class Ledger {
    private static final int HISTORY = 200;

    private long cash;
    private long seq;
    /** Lifetime guest revenue (only guest transactions, never purchases/refunds). Used for XP. */
    private long lifetimeGuestRevenue;
    private final Map<TxKind, Long> totals = new EnumMap<>(TxKind.class);
    private final Deque<Transaction> recent = new ArrayDeque<>();

    public Ledger(long startingCash) { this.cash = startingCash; }

    public long cash() { return cash; }
    public long lifetimeGuestRevenue() { return lifetimeGuestRevenue; }
    public long total(TxKind kind) { return totals.getOrDefault(kind, 0L); }
    public List<Transaction> recent() { return new ArrayList<>(recent); }

    public boolean canAfford(long amount) { return amount <= cash; }

    /** Spends if affordable. Returns false and changes nothing otherwise. */
    public boolean trySpend(long amount, TxKind kind, String memo, long gameTime) {
        if (amount < 0) throw new IllegalArgumentException("negative spend");
        if (amount > cash) return false;
        record(-amount, kind, memo, gameTime);
        return true;
    }

    /** Operating costs may push cash negative (debt) rather than silently stopping a lift mid-ride. */
    public void charge(long amount, TxKind kind, String memo, long gameTime) {
        if (amount <= 0) return;
        record(-amount, kind, memo, gameTime);
    }

    public void earn(long amount, TxKind kind, String memo, long gameTime) {
        if (amount <= 0) return;
        record(amount, kind, memo, gameTime);
        if (kind.guestRevenue) lifetimeGuestRevenue += amount;
    }

    private void record(long signed, TxKind kind, String memo, long gameTime) {
        cash += signed;
        totals.merge(kind, signed, Long::sum);
        recent.addFirst(new Transaction(++seq, gameTime, kind, signed, memo));
        while (recent.size() > HISTORY) recent.removeLast();
    }
}
