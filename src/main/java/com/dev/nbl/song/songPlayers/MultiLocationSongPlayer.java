package com.dev.nbl.song.songPlayers;

import com.dev.nbl.NoteblocksLive;
import com.dev.nbl.util.PreciseNotes;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MultiLocationSongPlayer extends AbstractSongPlayer {
    private final Plugin plugin = NoteblocksLive.getInstance();
    private final PlayerManager manager;

    private final ConcurrentHashMap<Location, Vector3d> locations = new ConcurrentHashMap<>();
    private final double baseRadius;
    private double radius;
    private final String name;

    private ConcurrentHashMap<Location, ConcurrentMap<UUID, User>> listeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Location, Set<Player>> players = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Location, ScheduledTask> audienceTasks = new ConcurrentHashMap<>();

    public MultiLocationSongPlayer(ArrayList<Location> locations, double radius, String name) {
        this.radius = radius;
        this.baseRadius = radius;
        this.name = name;
        manager = PacketEvents.getAPI().getPlayerManager();

        locations.forEach(location -> {
            this.locations.put(location, new Vector3d(
                    location.getX(),
                    location.getY(),
                    location.getZ()
            ));
        });

        NoteblocksLive.getInstance().addSongPlayer(this, name);

        if (listeners == null) listeners = new ConcurrentHashMap<>();
    }

    public void addPlayer(Player player) {
        throw new IllegalStateException("Cannot add a player with no specified location to a multi location song player");
    }

    public void addPlayer(Location location, Player player) {
        if (player == null) return;

        User user = manager.getUser(player);

        listeners.computeIfAbsent(location, _ -> new ConcurrentHashMap<>()).put(player.getUniqueId(), user);
        players.computeIfAbsent(location, _ -> ConcurrentHashMap.newKeySet()).add(player);
    }

    public void removePlayer(Player player) {
        UUID uuid = player.getUniqueId();

        listeners.forEach((_, map) -> map.remove(uuid));
        players.forEach((_, map) -> map.remove(player));
    }

    public void removePlayer(UUID player) {
        listeners.forEach((_, map) -> map.remove(player));

        Player p = Bukkit.getPlayer(player);
        players.forEach((_, map) -> map.remove(p));
    }

    public void addLocation(Location location) {
        this.locations.put(location, new Vector3d(
                location.getX(),
                location.getY(),
                location.getZ()
        ));

        listeners.putIfAbsent(location, new ConcurrentHashMap<>());
        players.putIfAbsent(location, ConcurrentHashMap.newKeySet());

        if (audienceTasks.containsKey(location)) return;

        audienceTasks.putIfAbsent(location, getAudienceTask(location));
    }

    public void removeLocation(Location location) {
        locations.remove(location);
        listeners.remove(location);
        players.remove(location);

        ScheduledTask scheduledTask = audienceTasks.remove(location);
        if (scheduledTask != null) scheduledTask.cancel();
    }

    private ScheduledTask getAudienceTask(Location location) {
        return plugin.getServer()
                .getRegionScheduler()
                .runAtFixedRate(
                        plugin,
                        location,
                        _ -> {
                            listeners.computeIfAbsent(location, _ -> new ConcurrentHashMap<>()).clear();
                            players.computeIfAbsent(location, _ -> ConcurrentHashMap.newKeySet()).clear();

                            for (Player player : location.getWorld().getNearbyPlayers(location, radius))
                                addPlayer(location, player);
                        },
                        1L,
                        5L
                );
    }

    @Override
    protected void updateVolume() {
        if (volume <= 1) radius = baseRadius;
        else radius = baseRadius * volume;
    }

    public void stopSong() {
        stopNoRemove();

        NoteblocksLive.getInstance().removeSongPlayer(name);
    }

    public void stopNoRemove() {
        stopPlaybackOnly();

        if (!audienceTasks.isEmpty()) {
            audienceTasks.values().forEach(ScheduledTask::cancel);
            audienceTasks.clear();
        }

        listeners.clear();
        players.clear();
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
        for (Location location : locations.keySet())
            audienceTasks.putIfAbsent(location, getAudienceTask(location));
    }

    @Override
    protected void sendAudienceMessage(Component message) {
        for (Set<Player> players : this.players.values())
            for (Player p : players) p.sendActionBar(message);
    }

    @Override
    protected void playPacketNote(PreciseNotes.PacketPreciseNote note) {
        if (listeners.isEmpty()) return;

        for (Map.Entry<Location, ConcurrentMap<UUID, User>> entry : listeners.entrySet()) {
            Vector3d position = locations.get(entry.getKey());
            if (entry.getValue().isEmpty()) continue;

            var packet = new WrapperPlayServerSoundEffect(
                    note.sound(),
                    SoundCategory.RECORD,
                    position,
                    (float) note.volume() * this.volume,
                    note.pitch()
            );

            for (User user : entry.getValue().values())
                if (user != null) user.sendPacket(packet);
        }
    }
}
