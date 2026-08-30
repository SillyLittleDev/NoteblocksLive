package com.dev.nbl.song.songPlayers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.dev.nbl.NoteblocksLive;
import com.dev.nbl.util.PreciseNotes;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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
    private final Plugin plugin = NoteblocksLive.getInstance();
    private final PlayerManager manager;

    private final Location location;
    private final Vector3i position;
    private final double radius;
    private final String name;

    private ConcurrentMap<UUID, User> listeners = new ConcurrentHashMap<>();
    private Set<Player> players = ConcurrentHashMap.newKeySet();

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

        NoteblocksLive.getInstance().addSongPlayer(this, name);

        if (listeners == null) listeners = new ConcurrentHashMap<>();
    }

    public void addPlayer(Player player) {
        if (player == null) return;

        User user = manager.getUser(player);

        listeners.put(player.getUniqueId(), user);
        players.add(player);
    }

    public void removePlayer(Player player) {
        listeners.remove(player.getUniqueId());
        players.remove(player);
    }

    public void removePlayer(UUID player) {
        listeners.remove(player);

        Player p = Bukkit.getPlayer(player);
        players.remove(p);
    }

    public void stopSong() {
        stopNoRemove();

        NoteblocksLive.getInstance().removeSongPlayer(name);
    }

    public void stopNoRemove() {
        stopPlaybackOnly();

        if (audienceTask != null) {
            audienceTask.cancel();
            audienceTask = null;
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
        audienceTask = plugin.getServer()
                .getRegionScheduler()
                .runAtFixedRate(
                        plugin,
                        location,
                        task -> {
                            listeners.clear();
                            players.clear();

                            for (Player player : location.getWorld().getNearbyPlayers(location, radius)) addPlayer(player);
                        },
                        1L,
                        5L
                );
    }

    @Override
    protected void sendAudienceMessage(Component message) {
        for (Player p : players) p.sendActionBar(message);
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
