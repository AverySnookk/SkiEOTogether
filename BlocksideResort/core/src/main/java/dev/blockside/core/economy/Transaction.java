package dev.blockside.core.economy;

/** One ledger line. amount > 0 is income, < 0 is spending. */
public record Transaction(long seq, long gameTime, TxKind kind, long amount, String memo) {}
