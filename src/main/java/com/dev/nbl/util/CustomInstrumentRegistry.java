package com.dev.nbl.util;

import com.dev.nbl.NoteblocksLive;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public final class CustomInstrumentRegistry {

    private final List<CustomInstrumentSoundSet> sets;
    private final Map<String, CustomInstrumentSoundSet> byId;

    public CustomInstrumentRegistry(List<CustomInstrumentSoundSet> sets) {
        this.sets = sets.stream()
                .sorted(Comparator.comparingInt(CustomInstrumentSoundSet::priority).reversed())
                .toList();

        Map<String, CustomInstrumentSoundSet> map = new HashMap<>();

        for (CustomInstrumentSoundSet set : sets) map.put(set.id(), set);

        this.byId = Map.copyOf(map);
    }

    public static CustomInstrumentRegistry empty() {
        return new CustomInstrumentRegistry(List.of());
    }

    public static CustomInstrumentRegistry load(Plugin plugin) {
        File folder = new File(plugin.getDataFolder(), "custom-instruments");

        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create custom-instruments folder.");
            return empty();
        }

        File[] files = folder.listFiles((dir, name) ->
                name.endsWith(".yml") || name.endsWith(".yaml"));

        if (files == null || files.length == 0) return empty();

        ArrayList<CustomInstrumentSoundSet> loaded = new ArrayList<>();

        for (File file : files) {
            try {
                loaded.add(CustomInstrumentSoundSet.load(file));
                plugin.getLogger().info("Loaded custom instrument sound set: " + file.getName());
            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "Failed to load custom instrument " + file.getName()
                                + ": " + exception.getMessage()
                );
            }
        }

        return new CustomInstrumentRegistry(loaded);
    }

    public CustomInstrumentSoundSet find(int channel, int program, int midiNote, long durationNs) {
        for (CustomInstrumentSoundSet set : sets)
            if (set.matches(channel, program, midiNote, durationNs)) return set;

        return null;
    }

    public boolean isCustomToken(String token) {
        return token != null && token.startsWith("custom:");
    }

    public int referenceC(String token) {
        CustomInstrumentSoundSet set = getSet(token);
        if (set == null) return 0;
        return set.referenceC();
    }

    public CustomInstrumentSoundSet getSet(String token) {
        String[] split = token.split(":");

        if (split.length != 3 || !split[0].equals("custom")) {
            NoteblocksLive.getInstance().getLogger().warning("Invalid custom instrument token: " + token);
            NoteblocksLive.getInstance().getLogger().warning("Will use a default sound.");
            return null;
        }

        CustomInstrumentSoundSet set = byId.get(split[1]);

        if (set == null) {
            NoteblocksLive.getInstance().getLogger().warning("Could not find custom instrument sound set for " + split[1]);
            return null;
        }

        return set;
    }

    public CustomInstrumentSoundSet.Part getPart(String token) {
        String[] split = token.split(":");

        if (split.length != 3 || !split[0].equals("custom")) {
            throw new IllegalArgumentException("Invalid custom instrument token: " + token);
        }

        return CustomInstrumentSoundSet.Part.fromKey(split[2]);
    }

    public String resolveSoundKey(String token, int bank) {
        CustomInstrumentSoundSet set = getSet(token);
        if (set == null) return token;
        CustomInstrumentSoundSet.Part part = getPart(token);

        return set.soundKey(part, bank);
    }
}