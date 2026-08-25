package com.dev.mcmidi.listeners;

import com.dev.mcmidi.MCMidi;
import com.dev.mcmidi.song.songPlayers.AbstractSongPlayer;
import com.dev.mcmidi.song.songPlayers.AudienceSongPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class EveryPlayerListener implements Listener {
    private final MCMidi musicManager;

    // Improve implementation of All Listener
    public EveryPlayerListener(MCMidi musicManager) {
        this.musicManager = musicManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        AbstractSongPlayer songPlayer = musicManager.getOtherPlayer("All-Listening");
        if (songPlayer == null) return;
        if (!(songPlayer instanceof AudienceSongPlayer asp)) return;

        asp.addPlayer(player);
    }
}
