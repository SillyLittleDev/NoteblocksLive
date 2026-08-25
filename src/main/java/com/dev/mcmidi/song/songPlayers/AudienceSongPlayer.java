package com.dev.mcmidi.song.songPlayers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.dev.mcmidi.MCMidi;
import com.dev.mcmidi.util.PreciseNotes;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AudienceSongPlayer extends AbstractSongPlayer {
    private final Plugin plugin = MCMidi.getInstance();
    private final PlayerManager packetEvents = PacketEvents.getAPI().getPlayerManager();
    private final ConcurrentHashMap<UUID, PacketAudienceMember> audience = new ConcurrentHashMap<>();
    private final String name;

    public AudienceSongPlayer(String name, Player... players) {
        for (Player player : players) audience.put(player.getUniqueId(), new PacketAudienceMember(packetEvents.getUser(player), player.getEntityId()));

        this.name = name;

        MCMidi.getInstance().addSongPlayer(this, name);
    }

    public void addPlayer(Player player) {
        audience.put(player.getUniqueId(), new PacketAudienceMember(packetEvents.getUser(player), player.getEntityId()));
    }

    public void removePlayer(UUID player) {
        audience.remove(player);
    }

    public void stopSong() {
        stopPlaybackOnly();
        MCMidi.getInstance().removeSongPlayer(name);
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
