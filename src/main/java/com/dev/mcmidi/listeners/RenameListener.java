package com.dev.mcmidi.listeners;

import com.dev.mcmidi.song.SongManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.UUID;

public class RenameListener implements Listener {
    private record Rename(long time, String oldName) {}
    private HashMap<UUID, Rename> renameMap = new HashMap<>();

    private final SongManager songManager;

    public RenameListener(SongManager songManager) {
        this.songManager = songManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!renameMap.containsKey(player.getUniqueId())) return;

        Rename rename = renameMap.get(player.getUniqueId());
        long diff = System.currentTimeMillis() - rename.time;

        if (diff > 15000) {
            renameMap.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);

        String name = PlainTextComponentSerializer.plainText().serialize(event.message());

        try {
            songManager.rename(rename.oldName, name);
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to rename song: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        player.sendMessage(
                Component.text("Song: ", NamedTextColor.WHITE)
                        .append(Component.text(rename.oldName, NamedTextColor.GREEN))
                        .append(Component.text(" has been renamed to ", NamedTextColor.WHITE))
                        .append(Component.text(name, NamedTextColor.GREEN))
        );
    }

    public void registerRename(UUID uuid, String oldName) {
        renameMap.put(uuid, new Rename(System.currentTimeMillis(), oldName));
    }
}
