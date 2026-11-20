package dev.blockside.core.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Block-built template. layers[y][z] is a row of characters indexed by x. Characters:
 * <ul>
 *   <li>' ' leave untouched</li>
 *   <li>'.' clear to air during site prep</li>
 *   <li>'@' the project/job block (anchor). Exactly one.</li>
 *   <li>anything else: palette entry.</li>
 * </ul>
 * Layer 0 is the foundation, laid one block below the anchor (i.e. replacing the top ground block).
 */
public final class Template {
    public String id;
    public String name;
    public int[] size;      // x, y, z
    public int[] entrance;  // x, y, z (template coords), side the guests use
    public Map<String, PaletteEntry> palette;
    public List<List<String>> layers;

    public static final class PaletteEntry {
        public String block;
        public Stage stage = Stage.STRUCTURE;
        public PaletteEntry() {}
        public PaletteEntry(String block, Stage stage) { this.block = block; this.stage = stage; }
    }

    public int sx() { return size[0]; }
    public int sy() { return size[1]; }
    public int sz() { return size[2]; }

    public char at(int x, int y, int z) {
        String row = layers.get(y).get(z);
        return x < row.length() ? row.charAt(x) : ' ';
    }

    /** Anchor cell (the '@'). */
    public int[] anchor() {
        for (int y = 0; y < sy(); y++) for (int z = 0; z < sz(); z++) for (int x = 0; x < sx(); x++)
            if (at(x, y, z) == '@') return new int[]{x, y, z};
        throw new IllegalStateException("template " + id + " has no '@' anchor");
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (size == null || size.length != 3) { errors.add("size must be [x,y,z]"); return errors; }
        if (layers == null || layers.size() != sy()) errors.add("expected " + sy() + " layers");
        else {
            int anchors = 0;
            for (int y = 0; y < sy(); y++) {
                if (layers.get(y).size() != sz()) errors.add("layer " + y + " needs " + sz() + " rows");
                for (int z = 0; z < Math.min(sz(), layers.get(y).size()); z++) {
                    String row = layers.get(y).get(z);
                    if (row.length() != sx()) errors.add("layer " + y + " row " + z + " needs " + sx() + " chars");
                    for (char c : row.toCharArray()) {
                        if (c == '@') anchors++;
                        else if (c != ' ' && c != '.' && (palette == null || !palette.containsKey(String.valueOf(c))))
                            errors.add("unknown palette char '" + c + "'");
                    }
                }
            }
            if (anchors != 1) errors.add("needs exactly one '@' anchor, found " + anchors);
        }
        return errors;
    }
}
