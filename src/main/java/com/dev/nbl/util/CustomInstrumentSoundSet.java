package com.dev.nbl.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class CustomInstrumentSoundSet {

    private static final int[] REQUIRED_SHIFT_BANKS = {-3, -2, -1, 1, 2, 3};

    public enum Part {
        SOUND("sound"),
        ATTACK("attack"),
        LOOP("loop"),
        RELEASE("release");

        private final String key;

        Part(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Part fromKey(String key) {
            return switch (key.toLowerCase(Locale.ROOT)) {
                case "sound" -> SOUND;
                case "attack" -> ATTACK;
                case "loop" -> LOOP;
                case "release" -> RELEASE;
                default -> throw new IllegalArgumentException("Unknown custom sound part: " + key);
            };
        }
    }

    public record Timing(
            long attackNs,
            long loopNs,
            long loopOverlapNs,
            long attackLoopOverlapNs,
            long loopReleaseOverlapNs,
            long releaseNs
    ) {}

    private final String id;
    private final String namespace;
    private final String instrument;
    private final boolean looping;
    private final int priority;
    private final int referenceC;
    private final long minDurationNs;
    private final long maxDurationNs;

    private final Set<Integer> channels;
    private final Set<Integer> programs;
    private final Set<Integer> notes;

    private final Timing defaultTiming;
    private final Map<Integer, Timing> shiftTimings;

    private CustomInstrumentSoundSet(
            String id,
            String namespace,
            String instrument,
            boolean looping,
            int priority,
            int referenceC,
            long minDurationNs,
            long maxDurationNs,
            Set<Integer> channels,
            Set<Integer> programs,
            Set<Integer> notes,
            Timing defaultTiming,
            Map<Integer, Timing> shiftTimings
    ) {
        this.id = id;
        this.namespace = namespace;
        this.instrument = instrument;
        this.looping = looping;
        this.priority = priority;
        this.referenceC = referenceC;
        this.minDurationNs = minDurationNs;
        this.maxDurationNs = maxDurationNs;
        this.channels = Set.copyOf(channels);
        this.programs = Set.copyOf(programs);
        this.notes = Set.copyOf(notes);
        this.defaultTiming = defaultTiming;
        this.shiftTimings = Map.copyOf(shiftTimings);
    }

    public static CustomInstrumentSoundSet load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String id = requireString(config, "id");
        String namespace = config.getString("namespace", "songs");
        String instrument = requireString(config, "instrument");

        boolean looping = config.getBoolean("looping", false);
        int priority = config.getInt("priority", 0);
        int referenceC = config.getInt("reference-c", 60);

        long minDurationNs = config.getLong("min-duration-ns", 0L);
        long maxDurationNs = config.getLong("max-duration-ns", Long.MAX_VALUE);

        Set<Integer> channels = intSet(config.getIntegerList("match.channels"));
        Set<Integer> programs = intSet(config.getIntegerList("match.programs"));
        Set<Integer> notes = intSet(config.getIntegerList("match.notes"));

        Timing defaultTiming = new Timing(0, 0, 0, 0, 0, 0);
        Map<Integer, Timing> shiftTimings = new HashMap<>();

        if (looping) {
            defaultTiming = readRequiredTiming(
                    file,
                    config,
                    "timings.default"
            );

            for (int bank : REQUIRED_SHIFT_BANKS) {
                String path = "timings.shifts." + bank;

                shiftTimings.put(
                        bank,
                        readRequiredTiming(file, config, path)
                );
            }
        }

        return new CustomInstrumentSoundSet(
                id,
                namespace,
                instrument,
                looping,
                priority,
                referenceC,
                minDurationNs,
                maxDurationNs,
                channels,
                programs,
                notes,
                defaultTiming,
                shiftTimings
        );
    }

    public boolean matches(int channel, int program, int midiNote, long durationNs) {
        return durationNs >= minDurationNs
                && durationNs <= maxDurationNs
                && matchesSet(channels, channel)
                && matchesSet(programs, program)
                && matchesSet(notes, midiNote);
    }

    public String token(Part part) {
        if (!looping && part != Part.SOUND) {
            throw new IllegalArgumentException(
                    "Non-looping custom instrument only supports SOUND part: " + id
            );
        }

        if (looping && part == Part.SOUND) {
            throw new IllegalArgumentException(
                    "Looping custom instrument requires ATTACK, LOOP, or RELEASE part: " + id
            );
        }

        return "custom:" + id + ":" + part.key();
    }

    public String soundKey(Part part, int bank) {
        String bankSuffix = bank == 0 ? "" : "_" + bank;

        if (!looping) {
            if (part != Part.SOUND) {
                throw new IllegalArgumentException(
                        "Non-looping custom instrument only has one sound: " + id
                );
            }

            return namespace + ":" + instrument + bankSuffix;
        }

        if (part == Part.SOUND) {
            throw new IllegalArgumentException(
                    "Looping custom instrument does not use SOUND part: " + id
            );
        }

        return namespace + ":" + instrument + bankSuffix + "_" + part.key();
    }

    public Timing timingForBank(int bank) {
        if (!looping) {
            throw new IllegalStateException(
                    "Non-looping custom instrument has no timing data: " + id
            );
        }

        return shiftTimings.getOrDefault(bank, defaultTiming);
    }

    public String id() {
        return id;
    }

    public boolean looping() {
        return looping;
    }

    public int priority() {
        return priority;
    }

    public int referenceC() {
        return referenceC;
    }

    public String namespace() {
        return namespace;
    }

    public String instrument() {
        return instrument;
    }

    private static Timing readRequiredTiming(File file, YamlConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);

        if (section == null) {
            throw new IllegalArgumentException(
                    file.getName() + " is missing required section: " + path
            );
        }

        requireLong(file, section, path, "attack-ns");
        requireLong(file, section, path, "loop-ns");
        requireLong(file, section, path, "release-ns");

        long loopOverlapNs = section.getLong("loop-overlap-ns", 0L);

        return new Timing(
                section.getLong("attack-ns"),
                section.getLong("loop-ns"),
                loopOverlapNs,
                section.getLong("attack-overlap-ns", loopOverlapNs),
                section.getLong("release-overlap-ns", loopOverlapNs),
                section.getLong("release-ns")
        );
    }

    private static void requireLong(File file, ConfigurationSection section, String sectionPath, String key) {
        if (!section.contains(key)) {
            throw new IllegalArgumentException(
                    file.getName() + " is missing required value: "
                            + sectionPath + "." + key
            );
        }
    }

    private static boolean matchesSet(Set<Integer> set, int value) {
        return set.isEmpty() || set.contains(value);
    }

    private static Set<Integer> intSet(List<Integer> values) {
        return new HashSet<>(values);
    }

    private static String requireString(YamlConfiguration config, String path) {
        String value = config.getString(path);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required key: " + path);
        }

        return value.trim();
    }
}