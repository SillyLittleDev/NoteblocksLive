package com.dev.nbl.song.songPlayers;

import com.google.common.collect.Queues;
import com.dev.nbl.NoteblocksLive;
import com.dev.nbl.util.PreciseNotes;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public abstract class AbstractSongPlayer {
    protected Iterator<PreciseNotes.PacketPreciseNote> song;
    protected ScheduledTask songTask;
    protected String songName;
    protected int loop = 0;
    protected float volume = 1;

    private static final long PRE_QUEUE_NOTE_TIME = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long MIN_REFILL_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(10);
    private static final int MAX_NOTE_QUEUE = 2048; // Per PRE_QUEUE_NOTE_TIME

    private ArrayList<PreciseNotes.PacketPreciseNote> songNotes;
    private ArrayList<TimedNoteGroup> noteGroups;
    private int nextGroupToQueue;
    private long songStartNs;
    private long playbackGeneration;

    private final Queue<ScheduledTask> queuedNoteTasks = new ConcurrentLinkedQueue<>();
    private final Queue<String> queue = Queues.newConcurrentLinkedQueue();

    /*
     * Used to force a song player to stop playing.
     */
    abstract public void stopSong();
    abstract public void startSong(ArrayList<PreciseNotes.PacketPreciseNote> song, String songName);
    public abstract void addPlayer(Player player);
    public abstract void removePlayer(UUID player);

    protected abstract void playPacketNote(PreciseNotes.PacketPreciseNote note);

    // todo: make more of the code generalized.
    public void startSong(String name) {
        startSong(NoteblocksLive.getSongManager().getMusicSheet(name), name);
    }

    protected synchronized void stopPlaybackOnly() {
        playbackGeneration++;
        cancelScheduledTasks();
        clearPlaybackState();
    }

    protected synchronized void startPlayback(ArrayList<PreciseNotes.PacketPreciseNote> notes) {
        playbackGeneration++;
        cancelScheduledTasks();

        songNotes = notes;
        song = notes.iterator();
        noteGroups = buildNoteGroups(notes);
        nextGroupToQueue = 0;
        songStartNs = System.nanoTime();

        if (noteGroups.isEmpty()) {
            clearPlaybackState();
            endSong();
            return;
        }

        queueLookahead(playbackGeneration);
    }

    private synchronized void queueLookahead(long generation) {
        if (generation != playbackGeneration || noteGroups == null) return;

        long nowNs = System.nanoTime();
        long queueUntilNs = safeAdd(nowNs, PRE_QUEUE_NOTE_TIME);
        int queuedThisRefill = 0;

        while (nextGroupToQueue < noteGroups.size()) {
            TimedNoteGroup group = noteGroups.get(nextGroupToQueue);
            long groupTargetNs = safeAdd(songStartNs, group.timeNs());

            if (groupTargetNs > queueUntilNs) break;

            queueGroup(generation, group, groupTargetNs);
            nextGroupToQueue++;

            if (++queuedThisRefill >= MAX_NOTE_QUEUE) break;
        }

        if (nextGroupToQueue < noteGroups.size()) {
            TimedNoteGroup nextGroup = noteGroups.get(nextGroupToQueue);
            long nextGroupTargetNs = safeAdd(songStartNs, nextGroup.timeNs());
            long refillTargetNs = nextGroupTargetNs - PRE_QUEUE_NOTE_TIME;
            long delayNs = Math.max(MIN_REFILL_DELAY_NS, refillTargetNs - System.nanoTime());

            Plugin plugin = NoteblocksLive.getInstance();

            songTask = Bukkit.getAsyncScheduler().runDelayed(
                    plugin,
                    task -> queueLookahead(generation),
                    delayNs,
                    TimeUnit.NANOSECONDS
            );
        } else {
            songTask = null;
        }
    }

    private void queueGroup(long generation, TimedNoteGroup group, long targetNs) {
        long delayNs = Math.max(0L, targetNs - System.nanoTime());
        Plugin plugin = NoteblocksLive.getInstance();

        if (delayNs <= 0L) {
            Bukkit.getAsyncScheduler().runNow(
                    plugin,
                    task -> playQueuedGroup(generation, group)
            );
            return;
        }

        ScheduledTask task = Bukkit.getAsyncScheduler().runDelayed(
                plugin,
                scheduledTask -> {
                    queuedNoteTasks.remove(scheduledTask);
                    playQueuedGroup(generation, group);
                },
                delayNs,
                TimeUnit.NANOSECONDS
        );

        queuedNoteTasks.add(task);
    }

    private void playQueuedGroup(long generation, TimedNoteGroup group) {
        boolean finalGroup;

        synchronized (this) {
            if (generation != playbackGeneration || noteGroups == null) return;
            finalGroup = group.index() == noteGroups.size() - 1;
        }

        for (PreciseNotes.PacketPreciseNote note : group.notes()) {
            playPacketNote(note);
        }

        if (finalGroup) {
            finishPlayback(generation);
        }
    }

    private void finishPlayback(long generation) {
        boolean shouldEnd;

        synchronized (this) {
            shouldEnd = generation == playbackGeneration && noteGroups != null;

            if (!shouldEnd) return;

            cancelScheduledTasks();
            clearPlaybackState();
        }

        endSong();
    }

    private void cancelScheduledTasks() {
        if (songTask != null) {
            songTask.cancel();
            songTask = null;
        }

        ScheduledTask queuedTask;
        while ((queuedTask = queuedNoteTasks.poll()) != null) queuedTask.cancel();
    }

    private void clearPlaybackState() {
        song = null;
        songNotes = null;
        noteGroups = null;
        nextGroupToQueue = 0;
        songStartNs = 0L;
    }

    private static ArrayList<TimedNoteGroup> buildNoteGroups(ArrayList<PreciseNotes.PacketPreciseNote> notes) {
        ArrayList<TimedNoteGroup> groups = new ArrayList<>();

        if (notes == null || notes.isEmpty()) return groups;

        long currentTimeNs = 0L;
        long groupTimeNs = 0L;
        ArrayList<PreciseNotes.PacketPreciseNote> currentGroup = new ArrayList<>();

        for (PreciseNotes.PacketPreciseNote note : notes) {
            if (currentGroup.isEmpty()) groupTimeNs = currentTimeNs;

            currentGroup.add(note);

            long pause = Math.max(0L, note.postPause());
            currentTimeNs = safeAdd(currentTimeNs, pause);

            if (pause > 0L) {
                groups.add(new TimedNoteGroup(
                        groupTimeNs,
                        List.copyOf(currentGroup),
                        groups.size()
                ));

                currentGroup.clear();
            }
        }

        if (!currentGroup.isEmpty()) {
            groups.add(new TimedNoteGroup(
                    groupTimeNs,
                    List.copyOf(currentGroup),
                    groups.size()
            ));
        }

        return groups;
    }

    private static long safeAdd(long left, long right) {
        long result = left + right;

        if (((left ^ result) & (right ^ result)) < 0) {
            return right >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }

        return result;
    }

    /*
     * Used to end a song. This can either be done automatically when a song completes, or manually to skip.
     */
    public void endSong() {
        if (loop != 0 && songName != null) {
            if (loop != -1) loop--;
            startSong(songName);
            return;
        }

        if (!queue.isEmpty()) startSong(queue.poll());
        else stopSong();
    }

    public void addToQueue(String songName) {
        queue.add(songName);
    }

    public void setLoops(int loop) {
        this.loop = loop;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    private record TimedNoteGroup(long timeNs, List<PreciseNotes.PacketPreciseNote> notes, int index) {}
}
