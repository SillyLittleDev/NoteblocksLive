package com.dev.mcmidi.util;

import java.util.Locale;
import java.util.Map;

public final class SoundKeyResolver {
    private static CustomInstrumentRegistry customInstrumentRegistry = CustomInstrumentRegistry.empty();

    private static final String CUSTOM_NAMESPACE = "songs";

    private static final int SEMITONES_PER_BANK = 24;
    private static final int OCTAVES_PER_BANK = 2;

    private static final int MIN_LOCAL_SEMITONE = -6;
    private static final int MAX_LOCAL_SEMITONE = 18;

    /*
     * An experimental toggle which doesnt persist. Due to how pitch shifting works, it tends to change volume.
     * This toggle replaces the audio with versions that have had their volume changed to match the original note.
     * It should be better, but can be disabled to test.
     */
    public static boolean useNormalized = true;

    public static void setCustomInstrumentRegistry(CustomInstrumentRegistry registry) {
        customInstrumentRegistry = registry == null ? CustomInstrumentRegistry.empty() : registry;
    }

    private static final Map<String, String> VANILLA_KEYS = Map.ofEntries(
            Map.entry("banjo", "minecraft:block.note_block.banjo"),
            Map.entry("basedrum", "minecraft:block.note_block.basedrum"),
            Map.entry("bass", "minecraft:block.note_block.bass"),
            Map.entry("bell", "minecraft:block.note_block.bell"),
            Map.entry("bit", "minecraft:block.note_block.bit"),
            Map.entry("chime", "minecraft:block.note_block.chime"),
            Map.entry("cow_bell", "minecraft:block.note_block.cow_bell"),
            Map.entry("didgeridoo", "minecraft:block.note_block.didgeridoo"),
            Map.entry("flute", "minecraft:block.note_block.flute"),
            Map.entry("guitar", "minecraft:block.note_block.guitar"),
            Map.entry("harp", "minecraft:block.note_block.harp"),
            Map.entry("hat", "minecraft:block.note_block.hat"),
            Map.entry("iron_xylophone", "minecraft:block.note_block.iron_xylophone"),
            Map.entry("pling", "minecraft:block.note_block.pling"),
            Map.entry("snare", "minecraft:block.note_block.snare"),
            Map.entry("xylophone", "minecraft:block.note_block.xylophone"),
            Map.entry("trumpet", "minecraft:block.note_block.trumpet"),
            Map.entry("trumpet_exposed", "minecraft:block.note_block.trumpet_exposed"),
            Map.entry("trumpet_weathered", "minecraft:block.note_block.trumpet_weathered"),
            Map.entry("trumpet_oxidized", "minecraft:block.note_block.trumpet_oxidized")
    );

    public static String getSoundKey(String sound, int octave, char tone, boolean sharp) {
        if (customInstrumentRegistry.isCustomToken(sound)) {
            int bank = calculateBank(octave, tone, sharp);
            return customInstrumentRegistry.resolveSoundKey(sound, bank);
        }

        // Let fully established non-registered custom sounds go through.
        if (sound.contains(":")) return sound;

        String instrument = normalizeInstrument(sound);
        validateInstrument(instrument);

        int bank = calculateBank(octave, tone, sharp);

        if (bank == 0) return VANILLA_KEYS.get(instrument);

        if (!useNormalized) return CUSTOM_NAMESPACE + ":" + instrument + "_" + bank;
        else return CUSTOM_NAMESPACE + ":" + instrument + "_" + bank + "_normalized";
    }

    public static int referenceC(String instrument) {
        if (customInstrumentRegistry.isCustomToken(instrument)) return customInstrumentRegistry.referenceC(instrument);

        return switch (instrument) {
            case "bass", "didgeridoo" -> 36;
            case "guitar" -> 48;
            case "bell", "chime", "flute", "xylophone" -> 72;
            default -> 60;
        };
    }

    public static int calculateNewOctave(int octave, char tone, boolean sharp) {
        int bank = calculateBank(octave, tone, sharp);

        return octave - (bank * OCTAVES_PER_BANK);
    }

    public static int calculateBank(int octave, char tone, boolean sharp) {
        int targetSemitone = octave * 12 + getToneSemitone(tone, sharp);

        if (targetSemitone >= MIN_LOCAL_SEMITONE
                && targetSemitone <= MAX_LOCAL_SEMITONE)
            return 0;

        if (targetSemitone > MAX_LOCAL_SEMITONE) {
            int distanceAboveRange = targetSemitone - MAX_LOCAL_SEMITONE;

            return ceilDiv(distanceAboveRange, SEMITONES_PER_BANK);
        }

        return Math.floorDiv(targetSemitone - MIN_LOCAL_SEMITONE, SEMITONES_PER_BANK);
    }

    private static int getToneSemitone(char tone, boolean sharp) {
        int semitone = switch (Character.toLowerCase(tone)) {
            case 'c' -> 0;
            case 'd' -> 2;
            case 'e' -> 4;
            case 'f' -> 5;
            case 'g' -> 7;
            case 'a' -> 9;
            case 'b' -> 11;
            default -> throw new IllegalArgumentException("Invalid tone: " + tone);
        };

        return semitone + (sharp ? 1 : 0);
    }

    private static int ceilDiv(int value, int div) {
        return Math.floorDiv(value + div - 1, div);
    }

    private static String normalizeInstrument(String sound) {
        if (sound == null || sound.isBlank())
            throw new IllegalArgumentException("Sound cannot be null or blank");


        return sound
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private static void validateInstrument(String instrument) {
        if (!VANILLA_KEYS.containsKey(instrument))
            throw new IllegalArgumentException("Unknown instrument: " + instrument);
    }
}
