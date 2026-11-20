package dev.blockside.core;

public enum HireResult {
    HIRED("Crew hired. Work begins now."),
    ASSIGNED("An idle resort crew was assigned."),
    ALREADY_STAFFED("This job already has a crew."),
    ALREADY_COMPLETE("This project is already complete."),
    INSUFFICIENT_FUNDS("Not enough resort cash for the crew."),
    NO_PERMISSION("You are not a member of this resort."),
    NOT_FOUND("No construction job here.");
    public final String message;
    HireResult(String m) { message = m; }
    public boolean ok() { return this == HIRED || this == ASSIGNED; }
}
