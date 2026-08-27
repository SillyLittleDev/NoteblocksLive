package com.dev.mcmidi.song;

import com.dev.mcmidi.MCMidi;
import com.dev.mcmidi.util.MidiFileConverter;
import com.dev.mcmidi.util.NBSFileConverter;
import com.dev.mcmidi.util.PreciseNotes;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;

public class SongManager {
    private final HashMap<String, ArrayList<PreciseNotes.PacketPreciseNote>> musicSheets = new HashMap<>();

    public ArrayList<PreciseNotes.PacketPreciseNote> getMusicSheet(String songName) {
        return musicSheets.get(songName);
    }

    public void rename(String oldName, String newName) throws IOException {
        File file = getFile(oldName, new File(MCMidi.getInstance().getDataFolder(), "songs"));
        if (file == null || !file.exists()) throw new FileNotFoundException("Song file could not be found.");

        ArrayList<PreciseNotes.PacketPreciseNote> musicSheet = getMusicSheet(oldName);
        if (musicSheet == null) throw new NullPointerException("Music sheet could not be found.");

        renameFile(file, newName);

        musicSheets.remove(oldName);

        musicSheets.put(newName, musicSheet);
    }

    private void renameFile(File sourceFile, String newName) throws IOException {
        String originalName = sourceFile.getName();
        String extension = "";

        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) extension = originalName.substring(dotIndex);

        Path sourcePath = sourceFile.toPath();
        Path targetPath = sourcePath.resolveSibling(newName + extension);

        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private File getFile(String name, File file) {
        if (!file.exists()) return null;

        File[] files = file.listFiles();

        if (files == null) return null;

        for (File f : files) {
            if (f == null) continue;

            if (f.isDirectory()) {
                getFile(name, f);
                continue;
            }

            String fileName = f.getName().toLowerCase();
            String songName = fileName.substring(0, fileName.lastIndexOf("."));

            if (!name.equalsIgnoreCase(songName)) continue;

            return f;
        }

        return null;
    }

    public void load() {
        musicSheets.clear();


        File file = new File(MCMidi.getInstance().getDataFolder(), "songs");
        if (!file.exists()) file.mkdir();

        loadFolder(file);
    }

    private void loadFolder(File file) {
        if (!file.exists()) return;

        File[] files = file.listFiles();

        if (files == null) return;

        for (File f : files) {
            if (f == null) continue;

            if (f.isDirectory()) {
                loadFolder(f);
                continue;
            }

            String name = f.getName().toLowerCase();

            try {
                if (name.endsWith(".yml") || name.endsWith(".yaml")) parseYML(f);
                else if (name.endsWith(".mid") || name.endsWith(".midi")) convertMidi(f);
                else if (name.endsWith(".nbs")) convertNBS(f);
                else if (name.endsWith(".mcmidi") || name.endsWith(".txt")) convertMCMidi(f);
                else MCMidi.getInstance().getLogger().severe("File not in YML or MIDI format: " + name);
            } catch (Exception e) {
                MCMidi.getInstance().getLogger().severe("Failed to load song: " + name);
                MCMidi.getInstance().getLogger().severe(e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    private void parseYML(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String name : config.getKeys(false)) {
            String data = config.getString(name);
            if (data == null || data.isEmpty()) continue;

            try {
                musicSheets.put(name, convertString(data));
            } catch (Exception e) {
                MCMidi.getInstance().getLogger().severe("Failed to load song: " + name + " - " + e.getMessage());
            }
        }
    }

    private void convertNBS(File file) {
        String name = file.getName().substring(0, file.getName().lastIndexOf("."));
        try {
            musicSheets.put(
                    name,
                    (MCMidi.getInstance().enableCustomSounds) ? NBSFileConverter.convertExtended(file): NBSFileConverter.convert(file)
            );
        } catch (Exception e) {
            MCMidi.getInstance().getLogger().severe("Failed to load song: " + name + " - " + e.getMessage());
            MCMidi.getInstance().getLogger().log(Level.SEVERE, "Failed to load NBS song: ", e);
        }
    }

    private void convertMidi(File file) {
        String name = file.getName().substring(0, file.getName().lastIndexOf('.'));

        try {
            String converted = MidiFileConverter.convertToSong(file);
            musicSheets.put(name, convertString(converted));
        } catch (Exception e) {
            MCMidi.getInstance().getLogger().severe("Failed to convert MIDI file for song: " + name);
            MCMidi.getInstance().getLogger().severe(e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private void convertMCMidi(File f) {
        String name = f.getName().substring(0, f.getName().lastIndexOf("."));

        try {
            String song = Files.readString(f.toPath());
            musicSheets.put(name, convertString(song));
        } catch (IOException e) {
            MCMidi.getInstance().getLogger().severe("Failed to read mcmidi song: " + name);
            MCMidi.getInstance().getLogger().severe(e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private ArrayList<PreciseNotes.PacketPreciseNote> convertString(String data) {
        ArrayList<PreciseNotes.PreciseNoteData> noteData = PreciseNotes.parseNotes(data.split(" "));
        return PreciseNotes.convertPacketNotes(noteData);
    }

    public Set<String> getSongNames() {
        return musicSheets.keySet();
    }

    public ArrayList<PreciseNotes.PacketPreciseNote> getSongNotes(String songName) {
        return musicSheets.get(songName);
    }
}
