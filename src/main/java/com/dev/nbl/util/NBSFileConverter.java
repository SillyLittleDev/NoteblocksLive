package com.dev.nbl.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.protocol.sound.Sounds;
import com.dev.nbl.NoteblocksLive;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

public final class NBSFileConverter {
    private static final Charset NBS_CHARSET = Charset.forName("windows-1252");

    private static final Sound[] VANILLA_SOUNDS = {
            Sounds.BLOCK_NOTE_BLOCK_HARP,
            Sounds.BLOCK_NOTE_BLOCK_BASS,
            Sounds.BLOCK_NOTE_BLOCK_BASEDRUM,
            Sounds.BLOCK_NOTE_BLOCK_SNARE,
            Sounds.BLOCK_NOTE_BLOCK_HAT,
            Sounds.BLOCK_NOTE_BLOCK_GUITAR,
            Sounds.BLOCK_NOTE_BLOCK_FLUTE,
            Sounds.BLOCK_NOTE_BLOCK_BELL,
            Sounds.BLOCK_NOTE_BLOCK_CHIME,
            Sounds.BLOCK_NOTE_BLOCK_XYLOPHONE,
            Sounds.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
            Sounds.BLOCK_NOTE_BLOCK_COW_BELL,
            Sounds.BLOCK_NOTE_BLOCK_DIDGERIDOO,
            Sounds.BLOCK_NOTE_BLOCK_BIT,
            Sounds.BLOCK_NOTE_BLOCK_BANJO,
            Sounds.BLOCK_NOTE_BLOCK_PLING,
            Sounds.BLOCK_NOTE_BLOCK_TRUMPET,
            Sounds.BLOCK_NOTE_BLOCK_TRUMPET_EXPOSED,
            Sounds.BLOCK_NOTE_BLOCK_TRUMPET_WEATHERED,
            Sounds.BLOCK_NOTE_BLOCK_TRUMPET_OXIDIZED
    };

    private static List<SoundCandidate> availableSounds;

    private static List<SoundCandidate> getAvailableSounds() {
        if (availableSounds != null) return availableSounds;

        List<SoundCandidate> sounds = new ArrayList<>();
        var version = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();

        for (Sound sound : Sounds.getRegistry().getEntries()) {
            if (sound.getId(version) < 0) continue;

            String registeredName = normalizeSoundName(sound.getName().toString());
            String soundId = normalizeSoundName(sound.getSoundId().toString());

            sounds.add(new SoundCandidate(sound, registeredName, soundId, basename(registeredName), basename(soundId)));
        }

        return availableSounds = List.copyOf(sounds);
    }

    public static ArrayList<PreciseNotes.PacketPreciseNote> convert(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("file cannot be null");

        if (!file.isFile()) throw new IOException("NBS file does not exist: " + file);

        Reader reader = new Reader(Files.readAllBytes(file.toPath()));

        Header header = readHeader(reader);
        List<RawNote> rawNotes = readNotes(reader, header.version());

        int maxLayer = rawNotes.stream()
                .mapToInt(RawNote::layer)
                .max()
                .orElse(-1);

        int[] layerVolumes = new int[Math.max(header.layerCount(), maxLayer + 1)];
        Arrays.fill(layerVolumes, 100);

        if (reader.remaining() > 0 && header.layerCount() > 0) readLayers(reader, header.version(), header.layerCount(), layerVolumes);

        List<NBSInstrument> customInstruments = reader.remaining() > 0 ? readCustomInstruments(reader) : List.of();

        ArrayList<TimedNote> timedNotes = new ArrayList<>(rawNotes.size());

        for (RawNote note : rawNotes) {
            Sound sound;
            int baseKey;

            if (note.instrument() < header.defaultInstrumentCount()) {
                if (note.instrument() >= VANILLA_SOUNDS.length)
                    throw new IllegalArgumentException("Unsupported vanilla NBS instrument: " + note.instrument());

                sound = VANILLA_SOUNDS[note.instrument()];
                baseKey = 45;
            } else {
                int customIndex = note.instrument() - header.defaultInstrumentCount();

                if (customIndex >= customInstruments.size())
                    throw new IllegalArgumentException("Invalid custom NBS instrument index: " + customIndex);

                NBSInstrument instrument = customInstruments.get(customIndex);

                sound = instrument.sound();
                baseKey = instrument.soundKey();
            }

            float pitch = (float) Math.pow(2.0, (((note.key() - baseKey) * 100.0) + note.pitchCents()) / 1200.0);
            double volume = (layerVolumes[note.layer()] * note.velocity()) / 10000.0;
            long timestamp = Math.round((note.tick() * 1000000000.0) / header.tempo());

            timedNotes.add(new TimedNote(timestamp, note.layer(), sound, pitch, volume));
        }

        timedNotes.sort(Comparator.comparingLong(TimedNote::timestamp).thenComparingInt(TimedNote::layer));

        ArrayList<PreciseNotes.PacketPreciseNote> result = new ArrayList<>(timedNotes.size());

        for (int i = 0; i < timedNotes.size(); i++) {
            TimedNote current = timedNotes.get(i);
            long postPause = i + 1 < timedNotes.size() ? Math.max(0L, timedNotes.get(i + 1).timestamp() - current.timestamp()) : 0;

            result.add(new PreciseNotes.PacketPreciseNote(current.sound(), current.pitch(), postPause, current.volume()));
        }

        return result;
    }

    public static ArrayList<PreciseNotes.PacketPreciseNote> convertExtended(File file) throws IOException {
        return PreciseNotes.convertPacketNotes(convertToData(file, false));
    }

    private static ArrayList<PreciseNotes.PreciseNoteData> convertToData(File file, boolean ignoreExtended) throws IOException {
        if (file == null) throw new IllegalArgumentException("file cannot be null");

        if (!file.isFile()) throw new IOException("NBS file does not exist: " + file);

        Reader reader = new Reader(Files.readAllBytes(file.toPath()));

        Header header = readHeader(reader);
        List<RawNote> rawNotes = readNotes(reader, header.version());

        int maxLayer = rawNotes.stream()
                .mapToInt(RawNote::layer)
                .max()
                .orElse(-1);

        int[] layerVolumes = new int[Math.max(header.layerCount(), maxLayer + 1)];
        Arrays.fill(layerVolumes, 100);

        if (reader.remaining() > 0 && header.layerCount() > 0) readLayers(reader, header.version(), header.layerCount(), layerVolumes);

        List<NBSInstrument> customInstruments = reader.remaining() > 0 ? readCustomInstruments(reader) : List.of();

        ArrayList<TimedPreciseNoteData> timedNotes = new ArrayList<>(rawNotes.size());

        for (RawNote note : rawNotes) {
            Sound sound;
            boolean custom;
            int baseKey;

            if (note.instrument() < header.defaultInstrumentCount()) {
                if (note.instrument() >= VANILLA_SOUNDS.length)
                    throw new IllegalArgumentException("Unsupported vanilla NBS instrument: " + note.instrument());

                sound = VANILLA_SOUNDS[note.instrument()];
                baseKey = 45;
                custom = false;
            } else {
                int customIndex = note.instrument() - header.defaultInstrumentCount();

                if (customIndex >= customInstruments.size())
                    throw new IllegalArgumentException("Invalid custom NBS instrument index: " + customIndex);

                NBSInstrument instrument = customInstruments.get(customIndex);

                sound = instrument.sound();
                baseKey = instrument.soundKey();
                custom = true;
            }

            int shift = (int) Math.round((((note.key() - baseKey) * 100.0) + note.pitchCents()) / 100.0);
            int noteIndex = 6 + shift;
            int octave = Math.floorDiv(noteIndex, 12);
            int pitchClass = Math.floorMod(noteIndex, 12);

            char tone = switch (pitchClass) {
                case 0, 1 -> 'C';
                case 2, 3 -> 'D';
                case 4 -> 'E';
                case 5, 6 -> 'F';
                case 7, 8 -> 'G';
                case 9, 10 -> 'A';
                case 11 -> 'B';
                default -> throw new IllegalStateException("Invalid pitch class: " + pitchClass);
            };

            boolean sharp = switch (pitchClass) {
                case 1, 3, 6, 8, 10 -> true;
                default -> false;
            };

            String soundKey;
            String rawSoundKey = sound.getName().toString();
            String croppedSoundKey = rawSoundKey.substring(rawSoundKey.lastIndexOf('.') + 1);

            if (!custom) {
                if (ignoreExtended) soundKey = croppedSoundKey;
                else soundKey = SoundKeyResolver.getSoundKey(croppedSoundKey, octave, tone, sharp);
            }
            else soundKey = rawSoundKey;

            int newOctave = (!ignoreExtended) ? SoundKeyResolver.calculateNewOctave(octave, tone, sharp) : octave;
            double volume = (layerVolumes[note.layer()] * note.velocity()) / 10000.0;
            long timestamp = Math.round((note.tick() * 1000000000.0) / header.tempo());

            timedNotes.add(new TimedPreciseNoteData(timestamp, note.layer(), soundKey, newOctave, tone, sharp, volume));
        }

        timedNotes.sort(Comparator.comparingLong(TimedPreciseNoteData::timestamp).thenComparingInt(TimedPreciseNoteData::layer));

        ArrayList<PreciseNotes.PreciseNoteData> result = new ArrayList<>(timedNotes.size());

        for (int i = 0; i < timedNotes.size(); i++) {
            TimedPreciseNoteData current = timedNotes.get(i);
            long postPause = i + 1 < timedNotes.size() ? Math.max(0L, timedNotes.get(i + 1).timestamp() - current.timestamp()) : 0;

            result.add(new PreciseNotes.PreciseNoteData(current.sound(), current.octave(), current.tone(), current.sharp(), postPause, current.volume()));
        }

        return result;
    }

    public static String convertToString(File file) throws IOException {
        ArrayList<PreciseNotes.PreciseNoteData> data = convertToData(file, true);
        StringBuilder result = new StringBuilder();

        for (PreciseNotes.PreciseNoteData current : data) {
            String stringBuilder =
                    current.sound() + ";" +
                    current.octave() + ";" +
                    current.tone() + ";" +
                    current.sharp() + ";" +
                    current.postPause() + ";" +
                    current.volume() + "; ";

            result.append(stringBuilder);
        }

        result.deleteCharAt(result.length() - 1);

        return result.toString();
    }


    private static List<NBSInstrument> readCustomInstruments(Reader reader) throws IOException {
        int count = reader.readUnsignedByte();

        ArrayList<NBSInstrument> instruments = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String name = reader.readString();
            String soundFile = reader.readString();
            int soundKey = reader.readUnsignedByte();

            reader.readUnsignedByte();

            instruments.add(new NBSInstrument(resolveCustomSound(name, soundFile), soundKey));
        }

        return instruments;
    }

    private static Header readHeader(Reader reader) throws IOException {
        int initialSongLength = reader.readUnsignedShort();

        int version;
        int defaultInstrumentCount;

        if (initialSongLength == 0) {
            version = reader.readUnsignedByte();

            if (version < 1 || version > 6) throw new IOException("Unsupported NBS version: " + version);

            defaultInstrumentCount = reader.readUnsignedByte();

            if (version >= 3) reader.readUnsignedShort();
        } else {
            version = 0;
            defaultInstrumentCount = 10;
        }

        int layerCount = reader.readUnsignedShort();

        reader.readString();
        reader.readString();
        reader.readString();
        reader.readString();

        double tempo = reader.readUnsignedShort() / 100.0;

        if (tempo <= 0.0) throw new IOException("Invalid NBS tempo: " + tempo);

        reader.readUnsignedByte();
        reader.readUnsignedByte();
        reader.readUnsignedByte();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readInt();
        reader.readString();

        if (version >= 4) {
            reader.readUnsignedByte();
            reader.readUnsignedByte();
            reader.readUnsignedShort();
        }

        return new Header(version, defaultInstrumentCount, layerCount, tempo);
    }

    private static List<RawNote> readNotes(Reader reader, int version) throws IOException {

        ArrayList<RawNote> notes = new ArrayList<>();

        int tick = -1;

        while (true) {
            int tickJump = reader.readUnsignedShort();

            if (tickJump == 0) break;

            tick += tickJump;

            int layer = -1;

            while (true) {
                int layerJump = reader.readUnsignedShort();

                if (layerJump == 0) break;

                layer += layerJump;

                int instrument = reader.readUnsignedByte();
                int key = reader.readUnsignedByte();

                int velocity = 100;
                int pitch = 0;

                if (version >= 4) {
                    velocity = reader.readUnsignedByte();

                    reader.readUnsignedByte();

                    pitch = reader.readSignedShort();
                }

                notes.add(new RawNote(tick, layer, instrument, key, velocity, pitch));
            }
        }

        return notes;
    }

    private static void readLayers(Reader reader, int version, int layerCount, int[] volumes) throws IOException {

        for (int layer = 0; layer < layerCount; layer++) {
            reader.readString();

            if (version >= 4) reader.readUnsignedByte();

            volumes[layer] = reader.readUnsignedByte();

            if (version >= 2) reader.readUnsignedByte();
        }
    }

    private static Sound resolveCustomSound(String instrumentName, String soundFile) {
        String file = normalizeSoundName(soundFile);
        String name = normalizeSoundName(instrumentName);

        String fileBase = basename(file);
        String nameBase = basename(name);
        List<String> hints = getSoundEventHints(soundFile, instrumentName);

        SoundCandidate best = null;
        int bestScore = Integer.MIN_VALUE;

        for (SoundCandidate candidate : getAvailableSounds()) {
            int score = scoreCandidate(file, fileBase, name, nameBase, candidate);

            for (String hint : hints) {
                if (hint.equals(candidate.registeredName())) score += 50000;
                if (hint.equals(candidate.soundId())) score += 50000;

                if (candidate.registeredName().endsWith(hint)) score += 25000;
                if (candidate.soundId().endsWith(hint)) score += 25000;

                score += tokenSimilarity(hint, candidate.registeredName()) * 500;
                score += tokenSimilarity(hint, candidate.soundId()) * 500;
            }

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best == null || bestScore < 1000)
            throw new IllegalArgumentException("Could not confidently map NBS custom instrument '" + instrumentName +
                    "' with sound file '" + soundFile + "' to a Minecraft sound.");

        NoteblocksLive.getInstance().getLogger().warning("Bound custom NBS sound: " + instrumentName + " to minecraft sound: " + best.registeredName);

        return best.sound();
    }

    private static List<String> getSoundEventHints(String soundFile, String instrumentName) {
        ArrayList<String> hints = new ArrayList<>();

        if (soundFile == null) return hints;

        String path = soundFile.toLowerCase(Locale.ROOT).replace('\\', '/');

        if (path.endsWith(".ogg") || path.endsWith(".wav")) path = path.substring(0, path.length() - 4);

        if (path.startsWith("minecraft/")) path = path.substring("minecraft/".length());
        if (path.startsWith("sounds/")) path = path.substring("sounds/".length());

        String[] parts = path.split("/");

        if (parts.length >= 3 && parts[0].equals("mob")) {
            String entity = parts[1];
            String sample = parts[parts.length - 1].replaceAll("\\d+$", "");

            String event = switch (sample) {
                case "hit", "hurt" -> "hurt";
                case "death", "die" -> "death";
                case "say", "idle", "ambient" -> "ambient";
                case "step" -> "step";
                case "attack" -> "attack";
                case "shoot" -> "shoot";
                case "jump" -> "jump";
                default -> sample;
            };

            hints.add(normalizeSoundName("entity." + entity + "." + event));
        }

        if (parts.length >= 2 && parts[0].equals("random")) {
            String sample = parts[parts.length - 1].replaceAll("\\d+$", "");
            hints.add(normalizeSoundName("random." + sample));
        }

        if (parts.length >= 2 && parts[0].equals("note")) {
            String sample = parts[parts.length - 1].replaceAll("\\d+$", "");
            hints.add(normalizeSoundName("block.note_block." + sample));
            hints.add(normalizeSoundName("block.note." + sample));
        }

        if (instrumentName != null && !instrumentName.isBlank()) {
            String normalizedName = normalizeSoundName(instrumentName);

            hints.add(normalizedName);

            if (parts.length >= 1 && parts[0].equals("mob")) hints.add(normalizeSoundName("entity." + instrumentName));
        }

        return hints;
    }

    private static int scoreCandidate(String nbsFile, String fileBase, String nbsName, String nameBase, SoundCandidate candidate) {
        int score = 0;

        String registered = candidate.registeredName();
        String soundId = candidate.soundId();

        if (!nbsFile.isEmpty()) {
            if (nbsFile.equals(soundId)) score += 20000;
            if (nbsFile.equals(registered)) score += 20000;

            if (soundId.endsWith(nbsFile)) score += 8000;
            if (registered.endsWith(nbsFile)) score += 8000;
        }

        if (!fileBase.isEmpty()) {
            if (fileBase.equals(candidate.soundIdBase())) score += 10000;
            if (fileBase.equals(candidate.registeredBase())) score += 10000;
        }

        if (!nbsName.isEmpty()) {
            if (nbsName.equals(soundId)) score += 4000;
            if (nbsName.equals(registered)) score += 4000;

            if (!nameBase.isEmpty()) {
                if (nameBase.equals(candidate.soundIdBase())) score += 3000;
                if (nameBase.equals(candidate.registeredBase())) score += 3000;
            }
        }

        if (registered.contains("note_block") || soundId.contains("note_block")) score += 500;

        score += tokenSimilarity(nbsFile, soundId) * 100;
        score += tokenSimilarity(nbsFile, registered) * 100;

        score += tokenSimilarity(nbsName, soundId) * 50;
        score += tokenSimilarity(nbsName, registered) * 50;

        if (!fileBase.isEmpty()) {
            score += fuzzyScore(fileBase, candidate.soundIdBase());
            score += fuzzyScore(fileBase, candidate.registeredBase());
        }

        if (!nameBase.isEmpty()) {
            score += fuzzyScore(nameBase, candidate.soundIdBase()) / 2;
            score += fuzzyScore(nameBase, candidate.registeredBase()) / 2;
        }

        return score;
    }

    private static int fuzzyScore(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;

        if (a.equals(b)) return 1000;

        int distance = levenshteinDistance(a, b);
        double similarity = 1.0 - ((double) distance / Math.max(a.length(), b.length()));

        return (int) Math.round(Math.max(0.0, similarity) * 1000.0);
    }

    private static int levenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) costs[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            int previous = costs[0];

            costs[0] = i;

            for (int j = 1; j <= b.length(); j++) {
                int current = costs[j];

                costs[j] = Math.min(Math.min(costs[j] + 1, costs[j - 1] + 1),
                        previous + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));

                previous = current;
            }
        }

        return costs[b.length()];
    }

    private static int tokenSimilarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;

        Set<String> bTokens = new HashSet<>(Arrays.asList(b.split("_")));

        int matching = 0;

        for (String token : a.split("_")) if (!token.isEmpty() && bTokens.remove(token)) matching++;

        return matching;
    }

    private static String normalizeSoundName(String value) {
        if (value == null) return "";

        String result = value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('\\', '/');

        if (result.endsWith(".ogg") || result.endsWith(".wav") || result.endsWith(".mp3"))
            result = result.substring(0, result.length() - 4);

        if (result.startsWith("assets/minecraft/sounds/")) result = result.substring("assets/minecraft/sounds/".length());

        if (result.startsWith("minecraft:sounds/")) result = result.substring("minecraft:sounds/".length());

        if (result.startsWith("sounds/")) result = result.substring("sounds/".length());

        if (result.startsWith("minecraft:")) result = result.substring("minecraft:".length());

        result = result
                .replace('/', '_')
                .replace('.', '_')
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("_+", "_");

        while (result.startsWith("_")) result = result.substring(1);

        while (result.endsWith("_")) result = result.substring(0, result.length() - 1);

        return result;
    }

    private static String basename(String value) {
        if (value == null || value.isEmpty()) return "";

        int underscore = value.lastIndexOf('_');

        return underscore == -1 ? value : value.substring(underscore + 1);
    }

    private record Header(int version, int defaultInstrumentCount, int layerCount, double tempo) {}

    private record RawNote(int tick, int layer, int instrument, int key, int velocity, int pitchCents) {}

    private record TimedNote(long timestamp, int layer, Sound sound, float pitch, double volume) {}

    private record TimedPreciseNoteData(long timestamp, int layer, String sound, int octave, char tone, boolean sharp, double volume) {}

    private record NBSInstrument(Sound sound, int soundKey) {}

    private record SoundCandidate(Sound sound, String registeredName, String soundId, String registeredBase, String soundIdBase) { }

    private static final class Reader {

        private final byte[] data;
        private int position;

        private Reader(byte[] data) {
            this.data = data;
        }

        private int remaining() {
            return data.length - position;
        }

        private int readUnsignedByte() throws IOException {
            require(1);

            return data[position++] & 0xFF;
        }

        private int readUnsignedShort() throws IOException {
            require(2);

            return (data[position++] & 0xFF) | ((data[position++] & 0xFF) << 8);
        }

        private short readSignedShort() throws IOException {
            return (short) readUnsignedShort();
        }

        private int readInt() throws IOException {
            require(4);

            return (data[position++] & 0xFF) |
                    ((data[position++] & 0xFF) << 8) |
                    ((data[position++] & 0xFF) << 16) |
                    ((data[position++] & 0xFF) << 24);
        }

        private String readString() throws IOException {
            int length = readInt();

            if (length < 0) throw new IOException("Negative NBS string length: " + length);

            require(length);

            String result = new String(data, position, length, NBS_CHARSET);

            position += length;

            return result;
        }

        private void require(int bytes) throws IOException {
            if (remaining() < bytes) throw new IOException("Unexpected end of NBS file at byte " + position);
        }
    }
}
