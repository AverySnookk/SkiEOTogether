package dev.blockside.core;

public record ActionResult(boolean ok, String message, String ref) {
    public static ActionResult ok(String msg) { return new ActionResult(true, msg, null); }
    public static ActionResult ok(String msg, String ref) { return new ActionResult(true, msg, ref); }
    public static ActionResult fail(String msg) { return new ActionResult(false, msg, null); }
}
