package dev.blockside.core.economy;

/**
 * Configurable XP milestone.
 * <ul>
 * <li>REVENUE: lifetime guest revenue >= threshold.</li>
 * <li>CONSTRUCTION: first completion of catalog entry {@code target} (or any project if target is "*"
 *     — then each distinct catalog entry pays once).</li>
 * <li>REPUTATION: reputation >= threshold continuously for {@code sustainTicks}.</li>
 * </ul>
 */
public record Milestone(String id, MilestoneKind kind, double threshold, String target, long sustainTicks,
                        long xp, String label) {}
