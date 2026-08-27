package com.dev.nbl.listeners;

import com.dev.nbl.NoteblocksLive;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerLeaveJoinListener implements Listener {
    private final NoteblocksLive musicManager;
    public String individualLoopSong; // maybe improve this. This was rushed and may have a better implementation.

    public PlayerLeaveJoinListener(NoteblocksLive musicManager) {
        this.musicManager = musicManager;
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        musicManager.removePlayer(e.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        musicManager.addPlayer(e.getPlayer());

        if (individualLoopSong != null) musicManager.playSong(e.getPlayer(), individualLoopSong, -1);
    }
}
