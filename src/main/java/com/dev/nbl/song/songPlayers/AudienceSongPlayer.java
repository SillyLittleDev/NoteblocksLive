package com.dev.nbl.song.songPlayers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.dev.nbl.NoteblocksLive;
import com.dev.nbl.util.PreciseNotes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;

public class AudienceSongPlayer extends AbstractSongPlayer {
    private final Plugin plugin = NoteblocksLive.getInstance();
    private final PlayerManager packetEvents = PacketEvents.getAPI().getPlayerManager();
    private final ConcurrentHashMap<UUID, PacketAudienceMember> audience = new ConcurrentHashMap<>();
    private final Set<Player> players = ConcurrentHashMap.newKeySet();
    private final String name;

    public AudienceSongPlayer(String name, Player... players) {
        for (Player player : players) audience.put(player.getUniqueId(), new PacketAudienceMember(packetEvents.getUser(player), player.getEntityId()));

        this.name = name;

        NoteblocksLive.getInstance().addSongPlayer(this, name);
    }

    public void addPlayer(Player player) {
        audience.put(player.getUniqueId(), new PacketAudienceMember(packetEvents.getUser(player), player.getEntityId()));
    }

    public void removePlayer(Player player) {
        audience.remove(player.getUniqueId());
        players.remove(player);
    }

    public void removePlayer(UUID player) {
        audience.remove(player);

        Player p = Bukkit.getPlayer(player);
        players.remove(p);
    }

    public void stopSong() {
        stopPlaybackOnly();
        NoteblocksLive.getInstance().removeSongPlayer(name);
    }

    public void startSong(ArrayList<PreciseNotes.PacketPreciseNote> song, String songName) {
        this.songName = songName;

        stopPlaybackOnly();

        if (song == null) {
            plugin.getLogger().severe("Song is null");
            endSong();
            return;
        }

        startPlayback(song);
    }

    @Override
    protected void sendAudienceMessage(Component message) {
        for (Player p : players) p.sendActionBar(message);
    }

    @Override
    protected void playPacketNote(PreciseNotes.PacketPreciseNote note) {
        final Sound sound = note.sound();
        final float volume = (float) note.volume();
        final float pitch = note.pitch();

        for (PacketAudienceMember member : audience.values()) {
            final User user = member.user();

            if (user == null) continue;

            user.sendPacket(new WrapperPlayServerEntitySoundEffect(
                    sound,
                    SoundCategory.RECORD,
                    member.entityId(),
                    volume * this.volume,
                    pitch
            ));
        }
    }

    public record PacketAudienceMember(User user, int entityId) {}
}
