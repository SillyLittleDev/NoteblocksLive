package com.dev.nbl.util;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;
import java.util.function.LongToDoubleFunction;

public final class MidiFileConverter {
    private static final String[] TONES = {
            "c;f", "c;s", "d;f", "d;s", "e;f", "f;f",
            "f;s", "g;f", "g;s", "a;f", "a;s", "b;f"
    };

    private static CustomInstrumentRegistry customInstrumentRegistry = CustomInstrumentRegistry.empty();

    public static String convertToSong(File midiFile) throws Exception {
        return midiFile == null ? null : convert(MidiSystem.getSequence(midiFile));
    }

    public static void setCustomInstrumentRegistry(CustomInstrumentRegistry registry) {
        customInstrumentRegistry = registry == null ? CustomInstrumentRegistry.empty() : registry;
    }

    private static String convert(Sequence sequence) {
        List<TimedMidiEvent> events = flatten(sequence);
        LongToDoubleFunction tickToNanos = buildTickConverter(sequence, events);

        int trackCount = sequence.getTracks().length;
        int[][] programs = new int[trackCount][16];

        Map<NoteKey, Deque<ActiveMidiNote>> activeNotes = new HashMap<>();
        ArrayList<ScheduledNote> scheduledNotes = new ArrayList<>();

        for (TimedMidiEvent timed : events) {
            MidiMessage message = timed.event().getMessage();

            if (!(message instanceof ShortMessage shortMessage)) continue;

            int trackIndex = timed.trackIndex();
            int channel = shortMessage.getChannel();
            int command = shortMessage.getCommand();
            int data1 = shortMessage.getData1();
            int data2 = shortMessage.getData2();

            if (command == ShortMessage.PROGRAM_CHANGE) {
                programs[trackIndex][channel] = data1;
                continue;
            }

            if (command != ShortMessage.NOTE_ON && command != ShortMessage.NOTE_OFF) {
                continue;
            }

            int midiNote = data1;
            int velocity = data2;

            boolean noteOn = command == ShortMessage.NOTE_ON && velocity > 0;
            boolean noteOff = command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0);

            long nanos = Math.round(tickToNanos.applyAsDouble(timed.event().getTick()));

            NoteKey key = new NoteKey(trackIndex, channel, midiNote);

            if (noteOn) {
                activeNotes
                        .computeIfAbsent(key, ignored -> new ArrayDeque<>())
                        .addLast(new ActiveMidiNote(
                                midiNote,
                                velocity,
                                programs[trackIndex][channel],
                                nanos,
                                channel,
                                trackIndex,
                                timed.eventIndex()
                        ));

                continue;
            }

            if (noteOff) {
                Deque<ActiveMidiNote> stack = activeNotes.get(key);

                if (stack == null || stack.isEmpty()) continue;

                ActiveMidiNote started = stack.removeFirst();

                long durationNs = Math.max(0L, nanos - started.startNs());

                InstrumentChoice choice = chooseInstrument(
                        started.channel(),
                        started.program(),
                        started.midiNote(),
                        durationNs
                );

                double volume = choice.isCustom()
                        ? Math.clamp(started.velocity() / 127.0, 0.08, 1.0)
                        : chooseVolume(
                        started.channel(),
                        choice.instrument(),
                        started.velocity()
                );

                StartedNote playableNote = new StartedNote(
                        choice,
                        started.midiNote(),
                        volume,
                        started.startNs(),
                        started.channel(),
                        started.trackIndex(),
                        started.eventIndex()
                );

                expandHeldNote(scheduledNotes, playableNote, nanos);
            }
        }

        if (scheduledNotes.isEmpty()) {
            throw new IllegalArgumentException("MIDI contains no playable note events.");
        }

        scheduledNotes.sort(Comparator
                .comparingLong(ScheduledNote::timeNs)
                .thenComparing(ScheduledNote::instrument)
                .thenComparingInt(ScheduledNote::midiNote)
                .thenComparingInt(ScheduledNote::channel)
                .thenComparingInt(ScheduledNote::trackIndex)
                .thenComparingInt(ScheduledNote::eventIndex));

        NavigableMap<Long, List<ScheduledNote>> groups = new TreeMap<>();

        for (ScheduledNote note : scheduledNotes) {
            groups.computeIfAbsent(note.timeNs(), ignored -> new ArrayList<>())
                    .add(note);
        }

        List<Long> times = new ArrayList<>(groups.keySet());
        StringBuilder song = new StringBuilder(scheduledNotes.size() * 32);

        for (int i = 0; i < times.size(); i++) {
            long time = times.get(i);
            long pause = i + 1 < times.size()
                    ? times.get(i + 1) - time
                    : 0L;

            List<ScheduledNote> notes = groups.get(time);

            for (int j = 0; j < notes.size(); j++) {
                if (!song.isEmpty()) {
                    song.append(' ');
                }

                song.append(encode(
                        notes.get(j),
                        j == notes.size() - 1 ? pause : 0L
                ));
            }
        }

        return song.toString();
    }

    private static List<TimedMidiEvent> flatten(Sequence sequence) {
        List<TimedMidiEvent> events = new ArrayList<>();
        Track[] tracks = sequence.getTracks();

        for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
            Track track = tracks[trackIndex];
            for (int eventIndex = 0; eventIndex < track.size(); eventIndex++)
                events.add(new TimedMidiEvent(track.get(eventIndex), trackIndex, eventIndex));
        }

        events.sort(Comparator
                .comparingLong((TimedMidiEvent event) -> event.event().getTick())
                .thenComparingInt(MidiFileConverter::eventPriority)
                .thenComparingInt(TimedMidiEvent::trackIndex)
                .thenComparingInt(TimedMidiEvent::eventIndex));
        return events;
    }

    private static int eventPriority(TimedMidiEvent event) {
        MidiMessage message = event.event().getMessage();

        if (message instanceof MetaMessage meta && meta.getType() == 0x51) return 0;

        if (message instanceof ShortMessage shortMessage) {
            int command = shortMessage.getCommand();

            if (command == ShortMessage.PROGRAM_CHANGE) return 1;

            if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && shortMessage.getData2() == 0)) return 2;

            if (command == ShortMessage.NOTE_ON) return 3;
        }

        return 4;
    }

    private static void expandHeldNote(List<ScheduledNote> out, StartedNote note, long endNs) {
        long durationNs = Math.max(0L, endNs - note.startNs());

        if (!note.choice().isCustom()) {
            out.add(new ScheduledNote(
                    note.choice().instrument(),
                    note.midiNote(),
                    note.volume(),
                    note.startNs(),
                    note.channel(),
                    note.trackIndex(),
                    note.eventIndex()
            ));

            return;
        }

        CustomInstrumentSoundSet set = note.choice().custom();

        if (set == null) {
            throw new IllegalStateException(
                    "InstrumentChoice was marked custom but the custom sound set was null: "
                            + note.choice().instrument()
            );
        }

        if (!set.looping()) {
            out.add(new ScheduledNote(
                    set.token(CustomInstrumentSoundSet.Part.SOUND),
                    note.midiNote(),
                    note.volume(),
                    note.startNs(),
                    note.channel(),
                    note.trackIndex(),
                    note.eventIndex()
            ));

            return;
        }

        int bank = bankFor(
                set.token(CustomInstrumentSoundSet.Part.ATTACK),
                note.midiNote()
        );

        CustomInstrumentSoundSet.Timing timing = set.timingForBank(bank);
        long startNs = note.startNs();

        out.add(new ScheduledNote(
                set.token(CustomInstrumentSoundSet.Part.ATTACK),
                note.midiNote(),
                note.volume(),
                startNs,
                note.channel(),
                note.trackIndex(),
                note.eventIndex()
        ));

        if (durationNs <= timing.attackNs() || timing.loopNs() <= 0L) {
            return;
        }

        long attackLoopOverlapNs = Math.max(0L, timing.attackLoopOverlapNs());
        long loopOverlapNs = Math.max(0L, timing.loopOverlapNs());
        long releaseNs = Math.max(0L, timing.releaseNs());

        long firstLoopStartNs = startNs
                + timing.attackNs()
                - attackLoopOverlapNs;

        long releaseStartNs = Math.max(firstLoopStartNs, endNs - releaseNs);

        long loopPeriodNs = Math.max(50_000_000L, timing.loopNs() - loopOverlapNs);

        for (long timeNs = firstLoopStartNs; timeNs < releaseStartNs; timeNs += loopPeriodNs) {
            out.add(new ScheduledNote(
                    set.token(CustomInstrumentSoundSet.Part.LOOP),
                    note.midiNote(),
                    note.volume(),
                    timeNs,
                    note.channel(),
                    note.trackIndex(),
                    note.eventIndex()
            ));
        }

        if (releaseNs > 0L) {
            out.add(new ScheduledNote(
                    set.token(CustomInstrumentSoundSet.Part.RELEASE),
                    note.midiNote(),
                    note.volume(),
                    releaseStartNs,
                    note.channel(),
                    note.trackIndex(),
                    note.eventIndex()
            ));
        }
    }

    private static LongToDoubleFunction buildTickConverter(Sequence sequence, List<TimedMidiEvent> events) {
        if (sequence.getDivisionType() != Sequence.PPQ) {
            double nanosPerTick = 1000000000.0 / (sequence.getDivisionType() * sequence.getResolution());
            return tick -> tick * nanosPerTick;
        }

        List<TempoPoint> points = new ArrayList<>();
        long previousTick = 0;
        double elapsedNanos = 0;
        int microsPerQuarter = 500000;
        points.add(new TempoPoint(0, 0, microsPerQuarter));

        for (TimedMidiEvent timed : events) {
            MidiMessage message = timed.event().getMessage();
            if (!(message instanceof MetaMessage meta) || meta.getType() != 0x51) continue;

            byte[] data = meta.getData();
            if (data.length != 3) continue;

            long tick = timed.event().getTick();
            elapsedNanos += (tick - previousTick) * (microsPerQuarter * 1000.0) / sequence.getResolution();
            previousTick = tick;
            microsPerQuarter = (data[0] & 0xFF) << 16 | (data[1] & 0xFF) << 8 | data[2] & 0xFF;
            TempoPoint point = new TempoPoint(tick, elapsedNanos, microsPerQuarter);

            if (points.getLast().tick() == tick) points.set(points.size() - 1, point);
            else points.add(point);
        }

        return tick -> {
            int low = 0;
            int high = points.size() - 1;

            while (low <= high) {
                int mid = low + high >>> 1;
                if (points.get(mid).tick() <= tick) low = mid + 1;
                else high = mid - 1;
            }

            TempoPoint point = points.get(high);
            return point.nanosAtTick()
                    + (tick - point.tick()) * (point.microsPerQuarter() * 1000.0) / sequence.getResolution();
        };
    }

    private static InstrumentChoice chooseInstrument(int channel, int program, int midiNote, long durationNs) {
        CustomInstrumentSoundSet custom = customInstrumentRegistry.find(channel, program, midiNote, durationNs);

        if (custom != null) {
            CustomInstrumentSoundSet.Part part = custom.looping()
                    ? CustomInstrumentSoundSet.Part.ATTACK
                    : CustomInstrumentSoundSet.Part.SOUND;

            return new InstrumentChoice(custom.token(part), custom);
        }

        return chooseDefaultInstrument(channel, program, midiNote);
    }

    private static InstrumentChoice chooseDefaultInstrument(int channel, int program, int midiNote) {
        if (channel == 9) return new InstrumentChoice(choosePercussionInstrument(midiNote), null);

        String instrument;

        if (program <= 7) instrument = program == 1 || program == 3 ? "pling" : "harp";
        else if (program <= 15) {
            instrument = switch (program) {
                case 9, 14 -> "bell";
                case 10 -> "bit";
                case 11 -> "iron_xylophone";
                case 12, 13 -> "xylophone";
                default -> "chime";
            };
        } else if (program <= 23) instrument = "didgeridoo";
        else if (program <= 31) instrument = "guitar";
        else if (program <= 39) instrument = "bass";
        else if (program <= 47) {
            instrument = switch (program) {
                case 45 -> "guitar";
                case 46 -> "harp";
                case 47 -> "basedrum";
                default -> "pling";
            };
        } else if (program <= 55) {
            instrument = switch (program) {
                case 48, 49, 50, 51 -> "flute";
                case 52, 53, 54 -> "chime";
                default -> "bell";
            };
        } else if (program <= 63) instrument = "didgeridoo";
        else if (program <= 79) instrument = "flute";
        else if (program <= 87) instrument = program <= 83 ? "bit" : "chime";
        else if (program <= 95) instrument = "chime";
        else if (program <= 103) instrument = "bell";
        else if (program <= 111) instrument = program == 105 ? "banjo" : "didgeridoo";
        else if (program <= 119) instrument = program == 115 ? "cow_bell" : "bell";
        else instrument = "hat";

        return new InstrumentChoice(instrument, null);
    }

    private static String choosePercussionInstrument(int midiNote) {
        return switch (midiNote) {
            case 35, 36, 41, 43, 45 -> "basedrum";
            case 37, 38, 39, 40, 47, 48, 50 -> "snare";
            case 56 -> "cow_bell";
            default -> "hat";
        };
    }

    private static double chooseVolume(int channel, String instrument, int velocity) {
        double scale = channel == 9
                ? switch (instrument) {
                    case "basedrum" -> 0.80;
                    case "snare" -> 0.72;
                    case "cow_bell" -> 0.55;
                    default -> 0.48;
                }
                : switch (instrument) {
                    case "didgeridoo", "bell", "chime", "xylophone", "iron_xylophone" -> 0.62;
                    case "guitar" -> 0.68;
                    case "bass" -> 0.72;
                    case "flute", "bit" -> 0.82;
                    default -> 0.70;
                };
        return Math.clamp(velocity / 127.0 * scale, 0.08, 1.0);
    }

    // Done as a string to allow for converting the midi into a text file.
    private static String encode(ScheduledNote note, long pause) {
        int relative = note.midiNote() - SoundKeyResolver.referenceC(note.instrument());

        return note.instrument()
                + ";"
                + Math.floorDiv(relative, 12)
                + ";"
                + TONES[Math.floorMod(relative, 12)]
                + ";"
                + pause
                + ";"
                + formatVolume(note.volume());
    }

    private static int referenceC(String instrument) {
        return SoundKeyResolver.referenceC(instrument);
    }

    private static int bankFor(String instrument, int midiNote) {
        int relative = midiNote - referenceC(instrument);
        int octave = Math.floorDiv(relative, 12);

        String[] toneData = TONES[Math.floorMod(relative, 12)].split(";");
        char tone = toneData[0].charAt(0);
        boolean sharp = toneData[1].equals("s");

        return SoundKeyResolver.calculateBank(octave, tone, sharp);
    }

    private static String formatVolume(double volume) {
        double rounded = Math.round(volume * 1000) / 1000.0;
        return rounded == (long) rounded ? Long.toString((long) rounded) : Double.toString(rounded);
    }

    private record InstrumentChoice(String instrument, CustomInstrumentSoundSet custom) {
        boolean isCustom() {
            return custom != null;
        }
    }

    private record NoteKey(int trackIndex, int channel, int midiNote) {}

    private record ActiveMidiNote(
            int midiNote,
            int velocity,
            int program,
            long startNs,
            int channel,
            int trackIndex,
            int eventIndex
    ) {}

    private record StartedNote(
            InstrumentChoice choice,
            int midiNote,
            double volume,
            long startNs,
            int channel,
            int trackIndex,
            int eventIndex
    ) {}

    private record ScheduledNote(
            String instrument,
            int midiNote,
            double volume,
            long timeNs,
            int channel,
            int trackIndex,
            int eventIndex
    ) {}

    private record TimedMidiEvent(MidiEvent event, int trackIndex, int eventIndex) {}
    private record TempoPoint(long tick, double nanosAtTick, int microsPerQuarter) {}
}
