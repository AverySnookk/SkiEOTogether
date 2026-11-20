package dev.blockside.core.catalog;

import dev.blockside.core.Json;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Data-driven project catalog + templates. */
public final class Catalog {
    public List<CatalogEntry> entries = new ArrayList<>();
    private transient Map<String, CatalogEntry> byId;
    private transient Map<String, Template> templates = new LinkedHashMap<>();

    public static Catalog parse(Reader json) {
        Catalog c = Json.GSON.fromJson(json, Catalog.class);
        c.index();
        return c;
    }

    /** Loads catalog.json and every referenced template via the given resource opener. */
    public static Catalog load(Function<String, java.io.InputStream> open) {
        try (var in = open.apply("blockside/catalog.json")) {
            if (in == null) throw new IllegalStateException("missing blockside/catalog.json");
            Catalog c = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (CatalogEntry e : c.entries) {
                for (String t : new String[]{e.template, e.upperTemplate}) {
                    if (t == null || c.templates.containsKey(t)) continue;
                    try (var tin = open.apply("blockside/templates/" + t + ".json")) {
                        if (tin == null) throw new IllegalStateException("missing template " + t + " for " + e.id);
                        Template tpl = Json.GSON.fromJson(new InputStreamReader(tin, StandardCharsets.UTF_8), Template.class);
                        List<String> errors = tpl.validate();
                        if (!errors.isEmpty()) throw new IllegalStateException("template " + t + ": " + errors);
                        c.templates.put(t, tpl);
                    }
                }
            }
            return c;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static Catalog loadBuiltin() {
        return load(p -> Catalog.class.getClassLoader().getResourceAsStream(p));
    }

    private void index() {
        byId = new LinkedHashMap<>();
        for (CatalogEntry e : entries) byId.put(e.id, e);
        if (templates == null) templates = new LinkedHashMap<>();
    }

    public CatalogEntry get(String id) { return byId.get(id); }
    public Template template(String id) { return id == null ? null : templates.get(id); }
    public void putTemplate(Template t) { templates.put(t.id, t); }
}
