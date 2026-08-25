package com.dev.mcmidi;

import com.dev.mcmidi.listeners.RenameListener;
import com.dev.mcmidi.song.songPlayers.*;
import com.github.retrooper.packetevents.PacketEvents;
import com.dev.mcmidi.commands.SongCommand;
import com.dev.mcmidi.listeners.EveryPlayerListener;
import com.dev.mcmidi.listeners.PlayerLeaveJoinListener;
import com.dev.mcmidi.listeners.ResourcePackListener;
import com.dev.mcmidi.song.SongManager;
import com.dev.mcmidi.util.CustomInstrumentRegistry;
import com.dev.mcmidi.util.MidiFileConverter;
import com.dev.mcmidi.util.PreciseNotes;
import com.dev.mcmidi.util.SoundKeyResolver;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

public final class MCMidi extends JavaPlugin {
    private static SongManager songManager;
    private static MCMidi musicManager;

    private final HashMap<Player, IndividualSongPlayer> songPlayers = new HashMap<>();
    private final HashMap<String, AbstractSongPlayer> otherPlayers = new HashMap<>(); // For location and follow players

    private CustomInstrumentRegistry customInstrumentRegistry;
    private boolean packetEventsLoaded;
    public boolean enableCustomSounds = true;
    public boolean enableDefaultResourcePack = true;
    public boolean requireResourcePack = true;

    private ResourcePackListener resourcePackListener;

    @Override
    public void onLoad() {
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
            PacketEvents.getAPI().load();

            packetEventsLoaded = true;
        } catch (Throwable throwable) {
            getLogger().log(Level.SEVERE, "PacketEvents failed during onLoad", throwable);
        }
    }

    @Override
    public void onEnable() {
        if (!packetEventsLoaded) {
            getLogger().severe("PacketEvents failed to load, disabling MCMidi.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        enableCustomSounds = getConfig().getBoolean("enable-custom-sounds", true);
        enableDefaultResourcePack = getConfig().getBoolean("use-default-pack-download", true);
        requireResourcePack = getConfig().getBoolean("require-resource-pack", true);

        PacketEvents.getAPI().init();
        musicManager = this;

        this.customInstrumentRegistry = CustomInstrumentRegistry.load(this);

        SoundKeyResolver.setCustomInstrumentRegistry(customInstrumentRegistry);
        MidiFileConverter.setCustomInstrumentRegistry(customInstrumentRegistry);

        songManager = new SongManager();
        songManager.load();

        PlayerLeaveJoinListener playerLeaveJoinListener = new PlayerLeaveJoinListener(this);
        RenameListener renameListener = new RenameListener(songManager);
        resourcePackListener = new ResourcePackListener(this);

        PluginManager pm = Bukkit.getPluginManager();

        pm.registerEvents(playerLeaveJoinListener, this);
        pm.registerEvents(new EveryPlayerListener(this), this);
        pm.registerEvents(resourcePackListener, this);
        pm.registerEvents(renameListener, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            new SongCommand(songManager, this, playerLeaveJoinListener, renameListener).register(commands);
        });

        saveResource("pack-26.2.zip", false);
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();

        getConfig().set("enable-custom-sounds", enableCustomSounds);
        saveDefaultConfig();
    }

    public void reload() {
        this.customInstrumentRegistry = CustomInstrumentRegistry.load(this);

        SoundKeyResolver.setCustomInstrumentRegistry(customInstrumentRegistry);
        MidiFileConverter.setCustomInstrumentRegistry(customInstrumentRegistry);
        songManager.load();

        enableDefaultResourcePack = getConfig().getBoolean("use-default-pack-download", true);
        requireResourcePack = getConfig().getBoolean("require-resource-pack", true);

        resourcePackListener.reloadPackRequest();
    }

    public HashMap<Player, IndividualSongPlayer> getSongPlayers() {
        return songPlayers;
    }

    public HashMap<String, AbstractSongPlayer> getOtherPlayers() {
        return otherPlayers;
    }

    public void addSongPlayer(AbstractSongPlayer songPlayer, String name) {
        if (getOtherPlayers().containsKey(name)) {
            AbstractSongPlayer sp = musicManager.getOtherPlayers().get(name);
            sp.stopSong();
            musicManager.getOtherPlayers().remove(name);
        }

        otherPlayers.put(name, songPlayer);
    }

    public void removeSongPlayer(String name) {
        otherPlayers.remove(name);
    }

    public void stopSongPlayer(String name) {
        if (!otherPlayers.containsKey(name)) return;

        AbstractSongPlayer sp = musicManager.getOtherPlayers().get(name);
        sp.stopSong();
        otherPlayers.remove(name);
    }

    public AbstractSongPlayer getOtherPlayer(String name) {
        return otherPlayers.get(name);
    }

    public IndividualSongPlayer getSongPlayer(Player player) {
        return songPlayers.get(player);
    }

    public void playSong(Player player, String song) {
        playSong(player, song, 0);
    }

    public void playSong(Player player, String song, int loop) {
        if (!songPlayers.containsKey(player)) songPlayers.put(player, new IndividualSongPlayer(player));

        IndividualSongPlayer sp = songPlayers.get(player);
        sp.startSong(song);
        sp.setLoops(loop);

        player.sendActionBar(
                Component.text("Now Playing: ", NamedTextColor.WHITE)
                        .append(Component.text(song, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
        );
    }


    public void stopSong(Player player) {
        if (!songPlayers.containsKey(player)) return;

        songPlayers.get(player).stopSong();
    }

    public void playSong(Player player, ArrayList<PreciseNotes.PacketPreciseNote> notes, String name) {
        if (!songPlayers.containsKey(player)) songPlayers.put(player, new IndividualSongPlayer(player));

        songPlayers.get(player).startSong(notes, name);
    }

    public void removePlayer(Player player) {
        songPlayers.remove(player);

        for (AbstractSongPlayer sp : songPlayers.values()) {
            if (sp instanceof AudienceSongPlayer asp) asp.removePlayer(player.getUniqueId());
            else if (sp instanceof LocationSongPlayer lsp) lsp.removePlayer(player.getUniqueId());
            else if (sp instanceof FollowSongPlayer fsp) fsp.removePlayer(player.getUniqueId());
        }
    }

    public void addPlayer(Player player) {
        songPlayers.put(player, new IndividualSongPlayer(player));
    }

    public static SongManager getSongManager() {
        return songManager;
    }

    public static MCMidi getInstance() {
        return musicManager;
    }
}
