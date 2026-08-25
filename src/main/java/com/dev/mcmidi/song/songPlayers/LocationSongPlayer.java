package com.dev.mcmidi.song.songPlayers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.dev.mcmidi.MCMidi;
import com.dev.mcmidi.util.PreciseNotes;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LocationSongPlayer extends AbstractSongPlayer {
    private final Plugin plugin = MCMidi.getInstance();
    private final PlayerManager manager;

    private final Location location;
    private final Vector3i position;
    private final double radius;
    private final String name;

    private ConcurrentMap<UUID, User> listeners = new ConcurrentHashMap<>();

    private ScheduledTask audienceTask;

    public LocationSongPlayer(Location location, double radius, String name) {
        this.location = location;
        this.radius = radius;
        this.name = name;
        manager = PacketEvents.getAPI().getPlayerManager();

        this.position = new Vector3i(
                (int) Math.floor(location.getX() * 8.0),
                (int) Math.floor(location.getY() * 8.0),
                (int) Math.floor(location.getZ() * 8.0)
        );

        MCMidi.getInstance().addSongPlayer(this, name);

        if (listeners == null) listeners = new ConcurrentHashMap<>();
    }

    public void addPlayer(Player player) {
        if (player == null) return;

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

    public void stopNoRemove() {
        stopPlaybackOnly();

        if (audienceTask != null) {
            audienceTask.cancel();
            audienceTask = null;
        }

        listeners.clear();
    }

    public void startSong(ArrayList<PreciseNotes.PacketPreciseNote> song, String name) {
        this.songName = name;

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
        audienceTask = plugin.getServer()
                .getRegionScheduler()
                .runAtFixedRate(
                        plugin,
                        location,
                        task -> {
                            Set<UUID> nearbyPlayers = new HashSet<>();

                            for (Player player : location.getWorld().getNearbyPlayers(location, radius)) {
                                nearbyPlayers.add(player.getUniqueId());
                                addPlayer(player);
                            }

                            listeners.keySet().removeIf(uuid -> !nearbyPlayers.contains(uuid));
                        },
                        1L,
                        5L
                );
    }

    @Override
    protected void playPacketNote(PreciseNotes.PacketPreciseNote note) {
        if (listeners.isEmpty()) return;

        var packet = new WrapperPlayServerSoundEffect(
                note.sound(),
                SoundCategory.RECORD,
                position,
                (float) note.volume() * this.volume,
                note.pitch()
        );

        for (User user : listeners.values()) {
            if (user != null) user.sendPacket(packet);
        }
    }
}
