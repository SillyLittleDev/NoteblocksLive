package com.dev.mcmidi.song.songPlayers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.dev.mcmidi.MCMidi;
import com.dev.mcmidi.util.PreciseNotes;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FollowSongPlayer extends AbstractSongPlayer {
    private final Plugin plugin = MCMidi.getInstance();
    private final PlayerManager manager = PacketEvents.getAPI().getPlayerManager();

    private final LivingEntity toFollow;
    private final int sourceEntityId;
    private final String name;

    private final ConcurrentMap<UUID, User> listeners = new ConcurrentHashMap<>();
    private ScheduledTask audienceTask;

    public FollowSongPlayer(LivingEntity entity, String name) {
        this.toFollow = entity;
        this.name = name;
        sourceEntityId = entity.getEntityId();

        MCMidi.getInstance().addSongPlayer(this, name);
    }

    public void addPlayer(Player player) {
        User user = manager.getUser(player);

        listeners.put(player.getUniqueId(), user);
    }

    public void removePlayer(UUID player) {
        listeners.remove(player);
    }

    public void stopSong() {
        stopNoRemove();

        MCMidi.getInstance().removeSongPlayer(name);
    }

    private void stopNoRemove() {
        stopPlaybackOnly();

        if (audienceTask != null) {
            audienceTask.cancel();
            audienceTask = null;
        }

        listeners.clear();
    }

    public void startSong(ArrayList<PreciseNotes.PacketPreciseNote> song, String songName) {
        this.songName = songName;

        stopNoRemove();

        if (song == null) {
            plugin.getLogger().severe("Song is null");
            endSong();
            return;
        }

        startListenerUpdates();
        startPlayback(song);
    }

    private void startListenerUpdates() {
        audienceTask = toFollow.getScheduler().runAtFixedRate(
                plugin,
                task -> updateListeners(),
                this::stopSong,
                1L,
                5L
        );
    }

    private void updateListeners() {
        Set<UUID> updated = ConcurrentHashMap.newKeySet();

        for (Player player : toFollow.getTrackedBy()) {
            updated.add(player.getUniqueId());
            addPlayer(player);
        }

        if (toFollow instanceof Player followedPlayer) {
            updated.add(followedPlayer.getUniqueId());
            addPlayer(followedPlayer);
        }

        listeners.keySet().removeIf(uuid -> !updated.contains(uuid));
    }

    @Override
    protected void playPacketNote(PreciseNotes.PacketPreciseNote note) {
        var packet = new WrapperPlayServerEntitySoundEffect(
                note.sound(),
                SoundCategory.RECORD,
                sourceEntityId,
                (float) note.volume() * this.volume,
                note.pitch()
        );

        for (User player : listeners.values()) {
            if (player != null) player.sendPacket(packet);
        }
    }
}
