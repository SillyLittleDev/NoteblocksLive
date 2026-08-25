package com.dev.mcmidi.song.songPlayers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.sound.SoundCategory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.dev.mcmidi.MCMidi;
import com.dev.mcmidi.util.PreciseNotes;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.UUID;

public class IndividualSongPlayer extends AbstractSongPlayer {
    private final Plugin plugin = MCMidi.getInstance();
    private final int playerID;
    private final User packetUser;

    public IndividualSongPlayer(Player player) {
        this.playerID = player.getEntityId();
        PlayerManager packetEvents = PacketEvents.getAPI().getPlayerManager();
        this.packetUser = packetEvents.getUser(player);
    }

    public User getPacketUser() {
        return packetUser;
    }

    public void stopSong() {
        stopPlaybackOnly();
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

    // todo: update to only have these in multi-audience players
    @Override
    public void addPlayer(Player player) {
    }

    @Override
    public void removePlayer(UUID player) {
    }

    @Override
    protected void playPacketNote(PreciseNotes.PacketPreciseNote note) {
        var packet = new WrapperPlayServerEntitySoundEffect(
                note.sound(),
                SoundCategory.RECORD,
                playerID,
                (float) note.volume() * this.volume,
                note.pitch()
        );

        packetUser.sendPacket(packet);
    }
}
