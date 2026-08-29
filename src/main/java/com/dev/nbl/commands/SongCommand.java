package com.dev.nbl.commands;

import com.dev.nbl.listeners.RenameListener;
import com.dev.nbl.song.songPlayers.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.dev.nbl.NoteblocksLive;
import com.dev.nbl.listeners.PlayerLeaveJoinListener;
import com.dev.nbl.song.SongManager;
import com.dev.nbl.util.MidiFileConverter;
import com.dev.nbl.util.NBSFileConverter;
import com.dev.nbl.util.PreciseNotes;
import com.dev.nbl.util.SoundKeyResolver;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SongCommand {
    private final SongManager songManager;
    private final NoteblocksLive musicManager;
    private final PlayerLeaveJoinListener playerLeaveJoinListener;
    private final RenameListener renameListener;

    public SongCommand(SongManager songManager, NoteblocksLive musicManager, PlayerLeaveJoinListener playerLeaveJoinListener, RenameListener renameListener) {
        this.songManager = songManager;
        this.musicManager = musicManager;
        this.playerLeaveJoinListener = playerLeaveJoinListener;
        this.renameListener = renameListener;
    }

    public void register(Commands commands) {
        LiteralCommandNode<CommandSourceStack> command = Commands.literal("songs")
                .requires(stack -> hasAnyPermission(stack.getSender()))
                .then(Commands.literal("reload")
                        .requires(stack -> stack.getSender().hasPermission("songs.reload"))
                        .executes(context -> {
                            musicManager.reload();
                            context.getSource().getSender().sendMessage(
                                    Component.text("Songs have been reloaded!", NamedTextColor.GREEN)
                            );

                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("play")
                        .requires(stack -> stack.getSender().hasPermission("songs.play.all") || stack.getSender().hasPermission("songs.play.individual"))
                        .then(Commands.literal("for")
                            .then(Commands.argument("target", StringArgumentType.string())
                                .suggests((context, builder) -> suggestPlayers(builder, context.getSource()))
                                .then(Commands.argument("song", StringArgumentType.greedyString())
                                        .suggests((_, builder) -> suggestSongs(builder))
                                        .executes(context -> {
                                            String string = StringArgumentType.getString(context, "target");
                                            String name;

                                            String song = StringArgumentType.getString(context, "song");
                                            ArrayList<PreciseNotes.PacketPreciseNote> notes = songManager.getSongNotes(song);

                                            if (notes == null || notes.isEmpty()) {
                                                context.getSource().getSender().sendMessage(
                                                        Component.text(
                                                                "Song Not Found!",
                                                                NamedTextColor.RED
                                                        )
                                                );

                                                return Command.SINGLE_SUCCESS;
                                            }

                                            if (string.equalsIgnoreCase("all")) {
                                                if (!context.getSource().getSender().hasPermission("songs.play.all")) {
                                                    context.getSource().getSender().sendMessage(Component.text("You do not have permission to play for all players!", NamedTextColor.RED));
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                AudienceSongPlayer sp = new AudienceSongPlayer("All-Listening", Bukkit.getOnlinePlayers().toArray(new Player[0]));

                                                sp.startSong(song);

                                                name = "All";
                                            }

                                            else {
                                                Player target = Bukkit.getPlayer(string);

                                                if (target == null) {
                                                    context.getSource().getSender().sendMessage(
                                                            Component.text(
                                                                    "Player Not Found!",
                                                                    NamedTextColor.RED
                                                            )
                                                    );

                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                if (context.getSource().getSender() instanceof Player p &&
                                                        !p.getUniqueId().equals(target.getUniqueId()) &&
                                                        !p.hasPermission("songs.play.all")) {
                                                    p.sendMessage(Component.text("You do not have permission to play for this players!", NamedTextColor.RED));
                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                musicManager.playSong(target, notes, song);

                                                name = target.getName();
                                            }


                                            context.getSource().getSender().sendMessage(
                                                    Component.text("Song: ", NamedTextColor.WHITE)
                                                            .append(Component.text(song, NamedTextColor.GREEN))
                                                            .append(Component.text(" is now playing for ", NamedTextColor.WHITE))
                                                            .append(Component.text(name, NamedTextColor.GREEN))
                                            );

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                            )
                        )
                        .then(Commands.literal("follow")
                                .requires(stack -> stack.getSender().hasPermission("songs.play.all"))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("song", StringArgumentType.greedyString())
                                                .suggests((_, builder) -> suggestSongs(builder))
                                                .executes(context -> {
                                                    if (!(context.getSource().getSender() instanceof LivingEntity player)) {
                                                        context.getSource().getSender().sendMessage(
                                                                Component.text("This must be used by a player.",  NamedTextColor.RED)
                                                        );

                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    String name = StringArgumentType.getString(context, "name");

                                                    String song = StringArgumentType.getString(context, "song");
                                                    ArrayList<PreciseNotes.PacketPreciseNote> notes = songManager.getSongNotes(song);

                                                    if (notes == null || notes.isEmpty()) {
                                                        context.getSource().getSender().sendMessage(
                                                                Component.text(
                                                                        "Song Not Found!",
                                                                        NamedTextColor.RED
                                                                )
                                                        );

                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    FollowSongPlayer fsPLayer = new FollowSongPlayer(player, name);
                                                    fsPLayer.startSong(notes, song);

                                                    player.sendMessage(
                                                            Component.text("Now playing song: ", NamedTextColor.WHITE)
                                                            .append(Component.text(song, NamedTextColor.GREEN))
                                                            .append(Component.text(" following you.", NamedTextColor.WHITE))
                                                    );

                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("location")
                                .requires(stack -> stack.getSender().hasPermission("songs.play.all"))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("song", StringArgumentType.greedyString())
                                                .suggests((_, builder) -> suggestSongs(builder))
                                                .executes(context -> {
                                                    if (!(context.getSource().getSender() instanceof LivingEntity player)) {
                                                        context.getSource().getSender().sendMessage(
                                                                Component.text("This must be used by a player.",  NamedTextColor.RED)
                                                        );

                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    String name = StringArgumentType.getString(context, "name");

                                                    String song = StringArgumentType.getString(context, "song");
                                                    ArrayList<PreciseNotes.PacketPreciseNote> notes = songManager.getSongNotes(song);

                                                    if (notes == null || notes.isEmpty()) {
                                                        context.getSource().getSender().sendMessage(
                                                                Component.text(
                                                                        "Song Not Found!",
                                                                        NamedTextColor.RED
                                                                )
                                                        );

                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    LocationSongPlayer lsPlayer = new LocationSongPlayer(player.getLocation(), 30, name);
                                                    lsPlayer.startSong(notes, song);

                                                    player.sendMessage(
                                                            Component.text("Now playing song: ", NamedTextColor.WHITE)
                                                                    .append(Component.text(song, NamedTextColor.GREEN))
                                                                    .append(Component.text(" at your location.", NamedTextColor.WHITE))
                                                    );

                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("individual-loop")
                                .requires(stack -> stack.getSender().hasPermission("songs.play.all"))
                                .then(Commands.argument("song", StringArgumentType.greedyString())
                                        .suggests((_, builder) -> suggestSongs(builder))
                                        .executes(context -> {
                                            String song = StringArgumentType.getString(context, "song");
                                            ArrayList<PreciseNotes.PacketPreciseNote> notes = songManager.getSongNotes(song);

                                            if (notes == null || notes.isEmpty()) {
                                                context.getSource().getSender().sendMessage(
                                                        Component.text(
                                                                "Song Not Found!",
                                                                NamedTextColor.RED
                                                        )
                                                );

                                                return Command.SINGLE_SUCCESS;
                                            }

                                            playerLeaveJoinListener.individualLoopSong = song;
                                            for (Player p : Bukkit.getOnlinePlayers()) musicManager.playSong(p, song, -1);

                                            context.getSource().getSender().sendMessage(
                                                    Component.text("Now playing song: ", NamedTextColor.WHITE)
                                                            .append(Component.text(song, NamedTextColor.GREEN))
                                                            .append(Component.text(" as an individual loop.", NamedTextColor.WHITE))
                                            );

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                                .executes(context -> {
                                    playerLeaveJoinListener.individualLoopSong = null;

                                    context.getSource().getSender().sendMessage(Component.text("Individual loop song disabled.", NamedTextColor.WHITE));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("controls")
                        .requires(stack -> stack.getSender().hasPermission("songs.control.all") || stack.getSender().hasPermission("songs.control.individual"))
                        .then(Commands.literal("queue")
                                .then(Commands.argument("song-player", StringArgumentType.string())
                                        .suggests((context, builder) -> suggestSongPlayer(builder, context.getSource()))
                                        .then(Commands.argument("song", StringArgumentType.greedyString())
                                                .suggests((_, builder) -> suggestSongs(builder))
                                                .executes(context -> {
                                                    String song = StringArgumentType.getString(context, "song");
                                                    ArrayList<PreciseNotes.PacketPreciseNote> notes = songManager.getSongNotes(song);

                                                    if (notes == null || notes.isEmpty()) {
                                                        context.getSource().getSender().sendMessage(
                                                                Component.text(
                                                                        "Song Not Found!",
                                                                        NamedTextColor.RED
                                                                )
                                                        );

                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    String string = StringArgumentType.getString(context, "song-player");

                                                    if (musicManager.getOtherPlayers().containsKey(string)) {
                                                        AbstractSongPlayer songPlayer = musicManager.getOtherPlayers().get(string);

                                                        if (blockToolCall(context.getSource().getSender(), songPlayer)) return Command.SINGLE_SUCCESS;

                                                        songPlayer.addToQueue(song);
                                                    }

                                                    else {
                                                        Player target = Bukkit.getPlayer(string);

                                                        if (target == null) {
                                                            context.getSource().getSender().sendMessage(
                                                                    Component.text(
                                                                            "Player Not Found!",
                                                                            NamedTextColor.RED
                                                                    )
                                                            );

                                                            return Command.SINGLE_SUCCESS;
                                                        }

                                                        IndividualSongPlayer isp = musicManager.getSongPlayer(target);

                                                        if (blockToolCall(context.getSource().getSender(), isp)) return Command.SINGLE_SUCCESS;

                                                        if (isp == null) {
                                                            context.getSource().getSender().sendMessage(
                                                                    Component.text(
                                                                            "Song Player Not Found!",
                                                                            NamedTextColor.RED
                                                                    )
                                                            );

                                                            return Command.SINGLE_SUCCESS;
                                                        }

                                                        isp.addToQueue(song);
                                                    }

                                                    context.getSource().getSender().sendMessage(
                                                            Component.text(song, NamedTextColor.GREEN)
                                                                    .append(Component.text(" was added to queue!", NamedTextColor.WHITE))
                                                    );
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("skip")
                                .then(Commands.argument("song-player", StringArgumentType.string())
                                        .suggests((context, builder) -> suggestSongPlayer(builder, context.getSource()))
                                        .executes(context -> {
                                            String string = StringArgumentType.getString(context, "song-player");
                                            if (string.equalsIgnoreCase("all")) string = "All-Listening";

                                            if (musicManager.getOtherPlayers().containsKey(string)) {
                                                AbstractSongPlayer asp = musicManager.getOtherPlayers().get(string);

                                                if (blockToolCall(context.getSource().getSender(), asp)) return Command.SINGLE_SUCCESS;

                                                asp.endSong();
                                            }
                                            else {
                                                Player target = Bukkit.getPlayer(string);

                                                if (target == null) {
                                                    context.getSource().getSender().sendMessage(
                                                            Component.text(
                                                                    "Player Not Found!",
                                                                    NamedTextColor.RED
                                                            )
                                                    );

                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                IndividualSongPlayer isp = musicManager.getSongPlayer(target);

                                                if (isp == null) {
                                                    context.getSource().getSender().sendMessage(
                                                            Component.text(
                                                                    "Song Player Not Found!",
                                                                    NamedTextColor.RED
                                                            )
                                                    );

                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                if (blockToolCall(context.getSource().getSender(), isp)) return Command.SINGLE_SUCCESS;

                                                isp.endSong();
                                            }

                                            context.getSource().getSender().sendMessage(
                                                    Component.text(
                                                            "Song skipped.",
                                                            NamedTextColor.GREEN
                                                    )
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                                .executes(context -> {
                                    if (!(context.getSource().getSender() instanceof Player player)) {
                                        context.getSource().getSender().sendMessage(
                                                Component.text(
                                                        "This command must be run by a player!",
                                                        NamedTextColor.RED
                                                )
                                        );
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    IndividualSongPlayer songPlayer = musicManager.getSongPlayer(player);
                                    if (songPlayer == null) {
                                        player.sendMessage(Component.text("Song Player Not Found!", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    songPlayer.endSong();
                                    player.sendMessage(Component.text("Song skipped.", NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("loop")
                                .then(Commands.argument("song-player", StringArgumentType.string())
                                        .suggests((context, builder) -> suggestSongPlayer(builder, context.getSource()))
                                        .then(Commands.argument("times", StringArgumentType.string())
                                                .suggests((_, builder) -> suggestLoop(builder))
                                                .executes(context -> {
                                                    String times = StringArgumentType.getString(context, "times");
                                                    int loops;
                                                    if (times.equalsIgnoreCase("infinite")) loops = -1;
                                                    else loops = Integer.parseInt(StringArgumentType.getString(context, "times"));

                                                    String string = StringArgumentType.getString(context, "song-player");
                                                    if (string.equalsIgnoreCase("all")) string = "All-Listening";

                                                    if (musicManager.getOtherPlayers().containsKey(string)) {
                                                        AbstractSongPlayer asp = musicManager.getOtherPlayers().get(string);

                                                        if (blockToolCall(context.getSource().getSender(), asp)) return Command.SINGLE_SUCCESS;

                                                        asp.setLoops(loops);
                                                    }
                                                    else {
                                                        Player target = Bukkit.getPlayer(string);

                                                        if (target == null) {
                                                            context.getSource().getSender().sendMessage(
                                                                    Component.text(
                                                                            "Player Not Found!",
                                                                            NamedTextColor.RED
                                                                    )
                                                            );

                                                            return Command.SINGLE_SUCCESS;
                                                        }

                                                        IndividualSongPlayer isp = musicManager.getSongPlayer(target);

                                                        if (blockToolCall(context.getSource().getSender(), isp)) return Command.SINGLE_SUCCESS;

                                                        if (isp == null) {
                                                            context.getSource().getSender().sendMessage(
                                                                    Component.text(
                                                                            "Song Player Not Found!",
                                                                            NamedTextColor.RED
                                                                    )
                                                            );

                                                            return Command.SINGLE_SUCCESS;
                                                        }

                                                        isp.setLoops(loops);
                                                    }

                                                    context.getSource().getSender().sendMessage(
                                                            Component.text("Song will loop: ", NamedTextColor.WHITE)
                                                                    .append(Component.text(times, NamedTextColor.GREEN))
                                                                    .append(Component.text(" times.", NamedTextColor.WHITE))
                                                    );
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("stop")
                                .then(Commands.argument("target", StringArgumentType.string())
                                        .suggests((context, builder) -> suggestSongPlayer(builder, context.getSource()))
                                        .executes(context -> {
                                            String string = StringArgumentType.getString(context, "target");
                                            String name;

                                            if (string.equalsIgnoreCase("all")) {
                                                musicManager.getSongPlayers().values().forEach(
                                                        IndividualSongPlayer::stopSong
                                                );

                                                if (blockToolCall(context.getSource().getSender(), null)) return Command.SINGLE_SUCCESS;

                                                List<String> other = musicManager.getOtherPlayers().keySet().stream().toList();
                                                other.forEach(musicManager::stopSongPlayer);

                                                name = "All";
                                            }

                                            else if (musicManager.getOtherPlayers().containsKey(string)) {
                                                AbstractSongPlayer songPlayer = musicManager.getOtherPlayers().get(string);

                                                if (blockToolCall(context.getSource().getSender(), songPlayer)) return Command.SINGLE_SUCCESS;

                                                songPlayer.stopSong();
                                                musicManager.getOtherPlayers().remove(string);

                                                name = string;
                                            }

                                            else {
                                                Player target = Bukkit.getPlayer(string);

                                                if (target == null) {
                                                    context.getSource().getSender().sendMessage(
                                                            Component.text(
                                                                    "Player Not Found!",
                                                                    NamedTextColor.RED
                                                            )
                                                    );

                                                    return Command.SINGLE_SUCCESS;
                                                }

                                                if (blockToolCall(context.getSource().getSender(), musicManager.getSongPlayer(target))) return Command.SINGLE_SUCCESS;

                                                musicManager.stopSong(target);

                                                name = target.getName();
                                            }

                                            context.getSource().getSender().sendMessage(
                                                    Component.text("Song stopped for player ", NamedTextColor.WHITE)
                                                            .append(Component.text(name, NamedTextColor.GREEN))
                                            );

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                                .executes(context -> {
                                    if (!(context.getSource().getSender() instanceof Player player)) {
                                        context.getSource().getSender().sendMessage(
                                                Component.text(
                                                        "This command must be run by a player!",
                                                        NamedTextColor.RED
                                                )
                                        );
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    IndividualSongPlayer songPlayer = musicManager.getSongPlayer(player);
                                    if (songPlayer == null) {
                                        player.sendMessage(Component.text("Song Player Not Found!", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    songPlayer.stopSong();
                                    player.sendMessage(Component.text("Song skipped.", NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("volume")
                                .then(Commands.argument("target", StringArgumentType.string())
                                        .suggests((context, builder) -> suggestSongPlayer(builder, context.getSource()))
                                        .then(Commands.argument("volume", DoubleArgumentType.doubleArg())
                                                .executes(context -> {
                                                    String string = StringArgumentType.getString(context, "target");
                                                    String name;

                                                    double volume = context.getArgument("volume", Double.class);
                                                    float convertedVolume;

                                                    if (volume > 1) convertedVolume = ((float) Math.clamp(volume, 0, 100)) / 100;
                                                    else convertedVolume = (float) volume;

                                                    if (string.equalsIgnoreCase("all")) {
                                                        AbstractSongPlayer songPlayer = musicManager.getOtherPlayer("All-Listening");

                                                        if (blockToolCall(context.getSource().getSender(), null)) return Command.SINGLE_SUCCESS;

                                                        if (songPlayer == null) {
                                                            context.getSource().getSender().sendMessage(
                                                                    Component.text(
                                                                            "All song player Not Found!",
                                                                            NamedTextColor.RED
                                                                    )
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        }

                                                        songPlayer.setVolume(convertedVolume);

                                                        name = "All";
                                                    }

                                                    else if (musicManager.getOtherPlayers().containsKey(string)) {
                                                        AbstractSongPlayer songPlayer = musicManager.getOtherPlayers().get(string);

                                                        if (blockToolCall(context.getSource().getSender(), songPlayer)) return Command.SINGLE_SUCCESS;

                                                        songPlayer.setVolume(convertedVolume);

                                                        name = string;
                                                    }

                                                    else {
                                                        Player target = Bukkit.getPlayer(string);

                                                        if (target == null) {
                                                            context.getSource().getSender().sendMessage(
                                                                    Component.text(
                                                                            "Player Not Found!",
                                                                            NamedTextColor.RED
                                                                    )
                                                            );

                                                            return Command.SINGLE_SUCCESS;
                                                        }

                                                        IndividualSongPlayer songPlayer = musicManager.getSongPlayer(target);

                                                        if (blockToolCall(context.getSource().getSender(), songPlayer)) return Command.SINGLE_SUCCESS;

                                                        songPlayer.setVolume(convertedVolume);

                                                        name = target.getName();
                                                    }

                                                    String volumeString = String.format("%.1f%%", convertedVolume * 100);

                                                    context.getSource().getSender().sendMessage(
                                                            Component.text("Volume for song player ", NamedTextColor.WHITE)
                                                                    .append(Component.text(name, NamedTextColor.GREEN))
                                                                    .append(Component.text(" set to ", NamedTextColor.WHITE))
                                                                    .append(Component.text(volumeString, NamedTextColor.GREEN))
                                                    );

                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("system")
                        .requires(stack -> stack.getSender().hasPermission("songs.system"))
                        .then(Commands.literal("toggle-normalized-audio")
                                .executes(context -> {
                                    SoundKeyResolver.useNormalized = !SoundKeyResolver.useNormalized;

                                    songManager.load();

                                    context.getSource().getSender().sendMessage(
                                            Component.text("Normalized audio is now: ", NamedTextColor.WHITE)
                                                    .append(Component.text(
                                                            (SoundKeyResolver.useNormalized) ? "Enabled" : "Disabled",
                                                            (SoundKeyResolver.useNormalized) ? NamedTextColor.GREEN : NamedTextColor.RED
                                                    ))
                                    );
                                    context.getSource().getSender().sendMessage(
                                            Component.text("Warning: Normalized audio is experimental but recommended to be on.", NamedTextColor.RED)
                                    );
                                    context.getSource().getSender().sendMessage(
                                            Component.text("If you have better results with it off, or issues with it on, please make a report.", NamedTextColor.RED)
                                    );

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("toggle-custom-sounds")
                                .executes(context -> {
                                    musicManager.enableCustomSounds = !musicManager.enableCustomSounds;

                                    musicManager.getConfig().set("enable-custom-sounds", musicManager.enableCustomSounds);
                                    musicManager.saveConfig();

                                    songManager.load();

                                    context.getSource().getSender().sendMessage(
                                            Component.text("Custom sounds are now: ", NamedTextColor.WHITE)
                                                    .append(Component.text(
                                                            (musicManager.enableCustomSounds) ? "Enabled" : "Disabled",
                                                            (musicManager.enableCustomSounds) ? NamedTextColor.GREEN : NamedTextColor.RED
                                                    ))
                                    );
                                    context.getSource().getSender().sendMessage(
                                            Component.text("Warning: Custom sounds is recommended to be on.", NamedTextColor.RED)
                                    );
                                    context.getSource().getSender().sendMessage(
                                            Component.text("Disabling it will make many songs not sound right, unless they are made with it in mind.", NamedTextColor.RED)
                                    );

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("toggle-now-playing")
                                .then(Commands.literal("-update")
                                        .executes(context -> {
                                            toggleNowPlayingMessage(context.getSource().getSender());

                                            boolean nowPlaying = AbstractSongPlayer.defaultNowPlaying;

                                            for (IndividualSongPlayer isp : musicManager.getSongPlayers().values()) isp.setNowPlaying(nowPlaying, true);
                                            for (AbstractSongPlayer asp : musicManager.getOtherPlayers().values()) asp.setNowPlaying(nowPlaying, true);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                                .executes(context -> {
                                    toggleNowPlayingMessage(context.getSource().getSender());

                                    context.getSource().getSender().sendMessage(
                                            Component.text("This disabled for all future song players created. To update current ones and players already logged in, add -update to the end of the command.",
                                                    NamedTextColor.YELLOW)
                                    );

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(Commands.literal("now-playing")
                                .then(Commands.literal("set-prefix")
                                        .then(Commands.argument("input", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String input = context.getArgument("input", String.class);
                                                    Component component = MiniMessage.miniMessage().deserialize(input);

                                                    AbstractSongPlayer.nowPlayingPrefix = component;

                                                    context.getSource().getSender().sendMessage(
                                                            Component.text("Now playing prefix is set to: ", NamedTextColor.WHITE)
                                                                    .append(component)
                                                    );

                                                    musicManager.getConfig().set("now-playing.prefix", input);
                                                    musicManager.saveConfig();

                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                                .then(Commands.literal("set-suffix")
                                        .then(Commands.argument("input", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String input = context.getArgument("input", String.class);
                                                    Component component = MiniMessage.miniMessage().deserialize(input);

                                                    AbstractSongPlayer.nowPlayingSuffix = component;

                                                    context.getSource().getSender().sendMessage(
                                                            Component.text("Now playing suiffix is set to: ", NamedTextColor.WHITE)
                                                                    .append(component)
                                                    );

                                                    musicManager.getConfig().set("now-playing.suffix", input);
                                                    musicManager.saveConfig();

                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                                .then(Commands.literal("reset")
                                        .executes(context -> {
                                            musicManager.getConfig().set("now-playing.prefix", "<light_purple>♫ </light_purple>");
                                            musicManager.getConfig().set("now-playing.suffix", "<light_purple> ♫</light_purple>");
                                            musicManager.saveConfig();

                                            context.getSource().getSender().sendMessage(
                                                    Component.text("Now playing prefix and suffix have been reset.", NamedTextColor.WHITE)
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(Commands.literal("utility")
                        .requires(stack -> stack.getSender().hasPermission("songs.utility"))
                        .then(Commands.literal("convert-to-string")
                                .then(Commands.argument("song", StringArgumentType.greedyString())
                                        .suggests((_, builder) -> suggestSongs(builder))
                                        .executes(context -> {
                                            String song = StringArgumentType.getString(context, "song");

                                            File file = new File(NoteblocksLive.getInstance().getDataFolder(), "songs");
                                            if (!file.exists()) {
                                                context.getSource().getSender().sendMessage(Component.text("Songs folder not found!", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            File songFile = getFile(song, file);
                                            if (songFile == null || !songFile.exists()) {
                                                context.getSource().getSender().sendMessage(Component.text("File not found!", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            String name = songFile.getName();
                                            String output = null;

                                            try {
                                                if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".txt")) context.getSource().getSender().sendMessage(Component.text("YML files are already saved as strings."));
                                                else if (name.endsWith(".mid") || name.endsWith(".midi")) output = MidiFileConverter.convertToSong(songFile);
                                                else if (name.endsWith(".nbs")) output = NBSFileConverter.convertToString(songFile);
                                                else NoteblocksLive.getInstance().getLogger().severe("File not in YML or MIDI format: " + name);
                                            } catch (Exception e) {
                                                NoteblocksLive.getInstance().getLogger().severe("Failed to parse song: " + name);
                                                NoteblocksLive.getInstance().getLogger().severe(e.getMessage());
                                                e.printStackTrace(System.err);

                                                context.getSource().getSender().sendMessage(Component.text("Failed to parse song: " + name, NamedTextColor.RED));
                                                context.getSource().getSender().sendMessage(Component.text("Check console for details.", NamedTextColor.RED));
                                            }

                                            if (output != null) {
                                                Component message = Component.text("Click to copy the song!", NamedTextColor.YELLOW, TextDecoration.UNDERLINED)
                                                        .hoverEvent(Component.text("Click to copy the song!", NamedTextColor.GREEN))
                                                        .clickEvent(ClickEvent.copyToClipboard(output));

                                                if (message.toString().length() < 60000)
                                                    context.getSource().getSender().sendMessage(message);
                                                else {
                                                    context.getSource().getSender().sendMessage(Component.text("Failed to copy song: " + song, NamedTextColor.RED));
                                                    context.getSource().getSender().sendMessage(Component.text("This often happens when a song is too long to copy. Check console to copy the song.", NamedTextColor.RED));
                                                    NoteblocksLive.getInstance().getLogger().info("Here is the song: " + output);
                                                }
                                            }

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("rename")
                                .then(Commands.argument("song", StringArgumentType.greedyString())
                                        .suggests((_, builder) -> suggestSongs(builder))
                                        .executes(context -> {
                                            if (!(context.getSource().getSender() instanceof Player player)) {
                                                context.getSource().getSender().sendMessage(Component.text("You must be a player to use this command.", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            String song = StringArgumentType.getString(context, "song");
                                            if (songManager.getSongNotes(song) == null) {
                                                player.sendMessage(Component.text("Song not found.", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            player.sendMessage(
                                                    Component.text("Send a message with the new name in chat. You have ", NamedTextColor.WHITE)
                                                            .append(Component.text("15", NamedTextColor.AQUA))
                                                            .append(Component.text(" seconds.", NamedTextColor.WHITE))
                                            );

                                            renameListener.registerRename(player.getUniqueId(), song);

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("info")
                                .then(Commands.argument("song", StringArgumentType.greedyString())
                                        .suggests((_, builder) -> suggestSongs(builder))
                                        .executes(context -> {
                                            String song = StringArgumentType.getString(context, "song");
                                            ArrayList<PreciseNotes.PacketPreciseNote> notes = songManager.getSongNotes(song);
                                            if (notes == null) {
                                                context.getSource().getSender().sendMessage(Component.text("Song not found.", NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            long durationNs = notes.stream()
                                                    .mapToLong(PreciseNotes.PacketPreciseNote::postPause)
                                                    .sum();

                                            int mins = (int) (durationNs / 60000000000L);
                                            int remainingSecs = (int) ((durationNs / 1000000000L) % 60);

                                            context.getSource().getSender().sendMessage(
                                                    Component.text("Song info: " + song, NamedTextColor.WHITE)
                                            );

                                            context.getSource().getSender().sendMessage(
                                                    Component.text("Notes: ", NamedTextColor.WHITE)
                                                            .append(Component.text(notes.size(), NamedTextColor.AQUA))
                                            );

                                            context.getSource().getSender().sendMessage(
                                                    Component.text("Duration: ", NamedTextColor.WHITE)
                                                            .append(Component.text(mins + ":" + String.format("%02d", remainingSecs), NamedTextColor.AQUA))
                                            );

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .build();

        commands.register(
                command,
                "Used to play and manage songs.",
                List.of("noteblockslive", "nbl")
        );
    }

    private void toggleNowPlayingMessage(CommandSender sender) {
        AbstractSongPlayer.defaultNowPlaying = !AbstractSongPlayer.defaultNowPlaying;

        musicManager.getConfig().set("default-now-playing", AbstractSongPlayer.defaultNowPlaying);
        musicManager.saveConfig();

        sender.sendMessage(
                Component.text("Now playing messages are now ", NamedTextColor.WHITE)
                        .append(Component.text(
                                (AbstractSongPlayer.defaultNowPlaying) ? "Enabled" : "Disabled",
                                (AbstractSongPlayer.defaultNowPlaying) ? NamedTextColor.GREEN : NamedTextColor.RED
                        ))
                        .append(Component.text(" by default.", NamedTextColor.WHITE))
        );
    }

    private boolean hasAnyPermission(CommandSender sender) {
        return sender.hasPermission("songs.command") ||
                sender.hasPermission("songs.play.individual") ||
                sender.hasPermission("songs.control.individual") ||
                sender.hasPermission("songs.play.all") ||
                sender.hasPermission("songs.control.all") ||
                sender.hasPermission("songs.utility") ||
                sender.hasPermission("songs.system") ||
                sender.hasPermission("songs.reload");
    }

    private boolean blockToolCall(CommandSender sender, AbstractSongPlayer songPlayer) {
        if (sender.hasPermission("songs.control.all")) return false;
        if (!sender.hasPermission("songs.control.individual")) {
            sender.sendMessage(Component.text("You do not have permission to control this song player.", NamedTextColor.RED));
            return true;
        }
        if (songPlayer == null) return true;

        if (!(songPlayer instanceof IndividualSongPlayer isp)) {
            sender.sendMessage(Component.text("You do not have permission to control this song player.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player p)) return false;

        if (isp.getPacketUser().getUUID().equals(p.getUniqueId())) return false;

        p.sendMessage(Component.text("You do not have permission to control this song player.", NamedTextColor.RED));
        return true;
    }

    private File getFile(String nameContains, File starting) {
        if (!starting.exists()) return null;
        File[] files = starting.listFiles();

        if (files == null) return null;

        File currentReturn = null;

        for (File file : files) {
            System.out.println("Checking " + file.getName());
            if (file.isDirectory()) {
                System.out.println("Checking directory");
                File check = getFile(nameContains, file);
                if (check != null) currentReturn = check;
            }
            else if (file.getName().contains(nameContains)) return file;
        }

        return currentReturn;
    }

    private CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder, CommandSourceStack source) {
        String message = builder.getRemaining().toLowerCase();

        if (!source.getSender().hasPermission("songs.play.all")) {
            builder.suggest(source.getSender().getName());
            return builder.buildFuture();
        }

        for (Player player : Bukkit.getOnlinePlayers())
            if (message.isEmpty() || player.getName().toLowerCase().contains(message))
                builder.suggest(player.getName());

        if ("all".contains(message))
            builder.suggest("All");

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSongPlayer(SuggestionsBuilder builder, CommandSourceStack source) {
        String message = builder.getRemaining().toLowerCase();

        if (!source.getSender().hasPermission("songs.control.all")) {
            builder.suggest(source.getSender().getName());
            return builder.buildFuture();
        }

        for (Player player : Bukkit.getOnlinePlayers())
            if (message.isEmpty() || player.getName().toLowerCase().contains(message))
                builder.suggest(player.getName());

        if ("all".contains(message))
            builder.suggest("All");

        for (String name : musicManager.getOtherPlayers().keySet())
            if (message.isEmpty() || name.toLowerCase().contains(message))
                builder.suggest(name);

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSongs(SuggestionsBuilder builder) {
        String message = builder.getRemaining().toLowerCase();

        for (String song : songManager.getSongNames()) {
            if (message.isEmpty() || song.toLowerCase().contains(message))
                builder.suggest(song);
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestLoop(SuggestionsBuilder builder) {
        int current = 2;

        try {
            String message = builder.getRemaining().toLowerCase();
            current = Integer.parseInt(message);
        } catch (NumberFormatException ignored) {}

        for (int i = current - 2; i < 5; i++) builder.suggest(i);
        builder.suggest("Infinite");

        return builder.buildFuture();
    }
}
