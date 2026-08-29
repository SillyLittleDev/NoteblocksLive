package com.dev.nbl.listeners;

import com.dev.nbl.NoteblocksLive;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.util.UUID;

public class ResourcePackListener implements Listener {
    private static final UUID uuid = UUID.randomUUID();
    private final NoteblocksLive plugin;
    ResourcePackRequest pack;

    public ResourcePackListener(NoteblocksLive plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.enableDefaultResourcePack) return;

        Audience.audience(event.getPlayer()).sendResourcePacks(pack);
    }

    public void reloadPackRequest() {
        pack = ResourcePackRequest.resourcePackRequest().
                replace(false)
                .required(plugin.requireResourcePack)
                .packs(ResourcePackInfo.resourcePackInfo()
                        .id(uuid)
                        .uri(URI.create("https://github.com/SillyLittleDev/MCMidi/blob/main/src/main/resources/pack-26.2.zip"))
                        .hash("6a51fdb8a9f1b25da85665033e0485db735c28bf")
                )
                .prompt(
                        Component.text("Would you like to enable the resource pack for MC Midi?", NamedTextColor.WHITE)
                                .append(Component.newline())
                                .append(Component.text("Warning: Not using this pack will likely make some custom music sound wrong.", NamedTextColor.RED))
                ) // test what this looks like
                .build();
    }
}
