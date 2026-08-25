package com.dev.mcmidi.util;

import com.github.retrooper.packetevents.protocol.sound.Sounds;
import com.dev.mcmidi.MCMidi;

import java.util.ArrayList;

public class PreciseNotes {
    public record PreciseNoteData(String sound, int octave, char tone, boolean sharp, long postPause, double volume) {}
    public record PacketPreciseNote(com.github.retrooper.packetevents.protocol.sound.Sound sound, float pitch, long postPause, double volume) {}

    public static ArrayList<PreciseNoteData> parseNotes(String[] s) {
        ArrayList<PreciseNoteData> notes = new ArrayList<>();

        for (String note : s) {
            String[] splitNote = note.split(";");

            if (splitNote.length < 5) throw new IllegalArgumentException("Note must be at least 5 arguments long: " + note);

            String sound = splitNote[0];
            int octave = Integer.parseInt(splitNote[1]);
            char tone = splitNote[2].charAt(0);
            boolean sharp = splitNote[3].equals("s");
            long postPause = Long.parseLong(splitNote[4]);
            double volume = (splitNote.length > 5) ? Double.parseDouble(splitNote[5]) : 1.0d;

            notes.add(
                    new PreciseNoteData(
                            (MCMidi.getInstance().enableCustomSounds) ? SoundKeyResolver.getSoundKey(sound, octave, tone, sharp) : sound,
                            (MCMidi.getInstance().enableCustomSounds) ? SoundKeyResolver.calculateNewOctave(octave, tone, sharp) : octave,
                            tone,
                            sharp,
                            postPause,
                            volume
                    )
            );
        }

        return notes;
    }

    public static ArrayList<PacketPreciseNote> convertPacketNotes(ArrayList<PreciseNoteData> data) {
        ArrayList<PacketPreciseNote> notes = new ArrayList<>();
        for (PreciseNoteData preciseNoteData : data) {
            notes.add(
                    new PacketPreciseNote(
                            Sounds.getByNameOrCreate(preciseNoteData.sound),
                            calculatePitch(preciseNoteData.tone, preciseNoteData.octave, preciseNoteData.sharp),
                            preciseNoteData.postPause,
                            preciseNoteData.volume
                    )
            );
        }

        return notes;
    }

    private static float calculatePitch(char tone, int octave, boolean isSharp) {
        int semitone = switch (tone) {
            case 'c','C' -> -6;
            case 'd','D' -> -4;
            case 'e','E' -> -2;
            case 'f','F' -> -1;
            case 'g','G' -> 1;
            case 'a','A' -> 3;
            case 'b','B' -> 5;
            default -> throw new IllegalStateException("Invalid Tone: " + tone);
        };

        if (isSharp) semitone++;

        return (float) (Math.pow(2, octave) * Math.pow(2, semitone / 12.0));
    }
}
