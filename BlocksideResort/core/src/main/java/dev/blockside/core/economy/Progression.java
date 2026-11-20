package dev.blockside.core.economy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resort XP and level. XP belongs to the resort (shared by all members). Every award is keyed by a
 * unique award id stored in {@link #awarded}; granting the same id twice is a no-op. Because awards are
 * evaluated on the server from resort state (not from a player's session), reconnecting, spamming
 * purchases/refunds, rebuilding, or oscillating around a threshold can never pay out twice.
 */
public final class Progression {
    private long xp;
    private final Set<String> awarded = new LinkedHashSet<>();
    /** Game tick at which reputation first rose above each REPUTATION milestone's threshold (-1 = below). */
    private final java.util.Map<String, Long> repAboveSince = new java.util.HashMap<>();
    private transient List<String> pendingNotices = new ArrayList<>();

    public long xp() { return xp; }
    public Set<String> awarded() { return Set.copyOf(awarded); }
    public boolean hasAward(String id) { return awarded.contains(id); }

    public int level(long[] levelXp) { return levelFor(xp, levelXp); }

    public static int levelFor(long xp, long[] levelXp) {
        int lvl = 1;
        for (int i = 1; i < levelXp.length; i++) if (xp >= levelXp[i]) lvl = i + 1;
        return lvl;
    }

    /** XP still needed to reach the next level, or 0 at max level. */
    public static long xpToNext(long xp, long[] levelXp) {
        int lvl = levelFor(xp, levelXp);
        return lvl >= levelXp.length ? 0 : levelXp[lvl] - xp;
    }

    /** Grants xp once per award id. Returns true if granted now. */
    public boolean grantOnce(String awardId, long amount, String label) {
        if (!awarded.add(awardId)) return false;
        xp += Math.max(0, amount);
        notices().add("+" + amount + " resort XP: " + label);
        return true;
    }

    public void onRevenue(List<Milestone> milestones, long lifetimeGuestRevenue) {
        for (Milestone m : milestones)
            if (m.kind() == MilestoneKind.REVENUE && lifetimeGuestRevenue >= m.threshold())
                grantOnce("milestone:" + m.id(), m.xp(), m.label());
    }

    /** Called when a construction job finishes. Award key is the catalog id, so rebuilding pays nothing. */
    public void onConstructionComplete(List<Milestone> milestones, String catalogId, String projectName) {
        for (Milestone m : milestones) {
            if (m.kind() != MilestoneKind.CONSTRUCTION) continue;
            if ("*".equals(m.target())) grantOnce("built:" + catalogId, m.xp(), m.label() + " (" + projectName + ")");
            else if (m.target().equals(catalogId)) grantOnce("milestone:" + m.id(), m.xp(), m.label());
        }
    }

    /** Reputation must stay at/above threshold for sustainTicks; dipping resets the timer. Pays once. */
    public void onReputation(List<Milestone> milestones, double reputation, long gameTime) {
        for (Milestone m : milestones) {
            if (m.kind() != MilestoneKind.REPUTATION) continue;
            String key = "milestone:" + m.id();
            if (awarded.contains(key)) continue;
            if (reputation >= m.threshold()) {
                long since = repAboveSince.computeIfAbsent(m.id(), k -> gameTime);
                if (gameTime - since >= m.sustainTicks()) grantOnce(key, m.xp(), m.label());
            } else {
                repAboveSince.remove(m.id());
            }
        }
    }

    /** Ticks reputation has been continuously above the milestone's threshold, or -1. */
    public long sustainedFor(String milestoneId, long gameTime) {
        Long s = repAboveSince.get(milestoneId);
        return s == null ? -1 : gameTime - s;
    }

    public List<String> drainNotices() {
        List<String> out = new ArrayList<>(notices());
        notices().clear();
        return out;
    }

    private List<String> notices() {
        if (pendingNotices == null) pendingNotices = new ArrayList<>();
        return pendingNotices;
    }
}
