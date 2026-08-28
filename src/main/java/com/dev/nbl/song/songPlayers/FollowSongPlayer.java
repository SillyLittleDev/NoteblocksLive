package com.dev.nbl.song.songPlayers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.dev.nbl.NoteblocksLive;
import com.dev.nbl.util.PreciseNotes;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FollowSongPlayer extends AbstractSongPlayer {
    private final Plugin plugin = NoteblocksLive.getInstance();
    private final PlayerManager manager = PacketEvents.getAPI().getPlayerManager();

    private final LivingEntity toFollow;
    private final int sourceEntityId;
    private final String name;

    private final ConcurrentMap<UUID, User> listeners = new ConcurrentHashMap<>();
    private final Set<Player> players = ConcurrentHashMap.newKeySet();
    private ScheduledTask audienceTask;

    public FollowSongPlayer(LivingEntity entity, String name) {
        this.toFollow = entity;
        this.name = name;
        sourceEntityId = entity.getEntityId();

        NoteblocksLive.getInstance().addSongPlayer(this, name);
    }

    public void addPlayer(Player player) {
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
                _ -> updateListeners(),
                this::stopSong,
                1L,
                5L
        );
    }

    private void updateListeners() {
        listeners.clear();
        players.clear();

        for (Player player : toFollow.getTrackedBy()) addPlayer(player);

        if (toFollow instanceof Player followedPlayer) addPlayer(followedPlayer);
    }

    @Override
    protected void sendAudienceMessage(Component message) {
        for (Player p : players) p.sendActionBar(message);
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
