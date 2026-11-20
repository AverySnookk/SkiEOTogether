package dev.blockside.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class Json {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
            .enableComplexMapKeySerialization().create();
    public static final Gson COMPACT = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    private Json() {}
}
