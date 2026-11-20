package dev.blockside.core.terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Batched, undoable world edit. Each entry stores the block spec before and after so an undo restores
 * the original exactly. The world adapter applies {@link #nextBatch} entries per tick.
 */
public final class EditJournal {
    public record Edit(int x, int y, int z, String before, String after) {}

    public String id;
    public String label;
    public final List<Edit> edits = new ArrayList<>();
    public int applied;
    public boolean undoing;
    public int undone;

    public boolean done() { return undoing ? undone >= applied : applied >= edits.size(); }

    /** Next edits to apply (forward) or revert (undo, newest first). */
    public List<Edit> nextBatch(int max) {
        List<Edit> out = new ArrayList<>();
        if (!undoing) {
            for (int i = applied; i < edits.size() && out.size() < max; i++) out.add(edits.get(i));
        } else {
            for (int i = applied - 1 - undone; i >= 0 && out.size() < max; i--) {
                Edit e = edits.get(i);
                out.add(new Edit(e.x(), e.y(), e.z(), e.after(), e.before()));
            }
        }
        return out;
    }

    public void advance(int n) { if (undoing) undone += n; else applied += n; }
}
