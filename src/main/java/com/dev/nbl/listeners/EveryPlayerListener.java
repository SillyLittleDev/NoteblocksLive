package com.dev.nbl.listeners;

import com.dev.nbl.NoteblocksLive;
import com.dev.nbl.song.songPlayers.AbstractSongPlayer;
import com.dev.nbl.song.songPlayers.AudienceSongPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class EveryPlayerListener implements Listener {
    private final NoteblocksLive musicManager;

    // Improve implementation of All Listener
    public EveryPlayerListener(NoteblocksLive musicManager) {
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
