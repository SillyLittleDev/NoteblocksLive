# Commands
At the moment, the plugin only has one main command, and all functions are sub commands of it. As such, each section will focus on sub commands.<br>
The command also has several aliases, so
```yml
/songs
/noteblockslive
/nbl
```
all will functionally be the same. I used /songs in the following examples as that is what I used in testing.

### NOTICE: If there is anything you feel this list is missing, or any details you feel should be added, feel free to reach out using the details in the [README](README.md).


## <u>Play</u>
```yml
/songs play
```
The play command allows users to play songs. This comes with several types of song players, each of which function a bit differently.

### Follow
```yml
/songs play follow <song player name> <song name>
```
This creates a song player which follows the individual who sent the command. Players around the individual will be able to hear the song as well.<br>
The song player name is just an internal identifier which can be used to control the player, as will be seen further down.

### For
```yml
/songs play for <Player> <song name>
```
The for sub command will play a song for an individual player. The song will only play for that player, and will originate from them, so it won't sound like its coming from any particular direction.<br>
This also supports using all as a target, which will play a song simultaneously for all players online. 

### Individual Loop
```yml
/songs play individual-loop <song name>
```
This has a song play separately for each player on the server. This is similar to using /songs play for all <song name>, except for how it handles new players.<br>
Players who join the server after the song has started will hear it from the start, where as play for all has the song synchronized for all player.

### Location
```yml
/songs play location <song player name> <song name>
```
Similarly to the follow sub command, location uses an internal identifier to be controlled. This command plays the song from the location where the command sender currently is.<br>
The song location will stay the same, and will get quieter as you get further away.



## <u>Controls</u>
```yml
/songs controls
```
The controls sub command allows you to control current song players. This is where you handle song queues, volume, looping, skipping, etc.

### Loop
```yml
/songs controls loop <song player> <loop count>
```
This will let you loop the song player a certain number of times. It can also be set to infinite.<br>
For individual players, use the players username, otherwise, use the internal identifier you set when creating the song player.

### Queue
```yml
/songs controls queue <song player> <song name>
```
This allows you to add a song to the queue.<br>
For individual players, use the players username, otherwise, use the internal identifier you set when creating the song player.

### Skip
```yml
/songs controls skip <song player>
```
This will skip the current song playing for a song player. If it is not looping and there is no queue, it will stop play entirely.<br>
For individual players, use the players username, otherwise, use the internal identifier you set when creating the song player.

### Stop
```yml
/songs controls stop <song player>
```
This will stop a song player, regardless of queue or loops.<br>
For individual players, use the players username, otherwise, use the internal identifier you set when creating the song player.

### Volume
```yml
/songs controls volume <song player> <volume>
```
This allows you to set the volume for a song player. Numbers from 1 to 100 will convert as percentages. Numbers from 0 to 1 will scale as well. As such, using 0.5 and 50 will both set the volume to 50%.<br>
For individual players, use the players username, otherwise, use the internal identifier you set when creating the song player.

## System
This sub command is used to control plugin wide settings. For the most part, it is suggested to leave this alone. These settings are mainly for testing.

### Toggle Custom Sounds
```yml
/songs system toggle-custom-sounds
```
This toggles the plugins of usage of custom sounds. It is always recommended to be on. Due to minecrafts limited sound system, this is used to allow sounds to be played at a wider range of notes.<br>
Turning this off will make many songs not sound right. Only have this off if you have the resource pack disabled, and don't have the resources given to players elsewhere.

### Toggle Normalized Audio
```yml
/songs system toggle-normalized-audio
```
This is purely an experimental setting which has been left in. This is recommended to always be on. This changes the extended sounds to use a different method of pitching.<br>
Disabling this will make notes sound louder or quieter based on pitch. This was an old setup used which was worse, and is only left in as a legacy testing setup.

### Toggle Now Playing
```yml
/songs system toggle-now-playing [-update]
```
This is the only system command I would suggest anyone ever use. This allows you to toggle on and off the now playing action bar message.<br>
The -update flag is optional. Having it included will reload all current song players to use the new now playing rule.<br>
If -update is left out, it will only apply to new song players. Keep in mind, this means any players who logged in before the change will not be updated, as song players for individual players persist until they log off.

## Utility
```yml
/songs utility
```
This is a utility command that allows you to use various tools.

### Convert To String
```yml
/songs utility convert-to-string <song name>
```
This converts a song to a string that can be copy and pasted. This string is in the custom txt format listed in the [README](README.md).<br>
While this string can be useful for more precise editing of songs, there is currently no clean program for editing them other than by hand.<br>
The strings are also usually too long to send in chat, and as such, would require the user to copy the output from the server console.

### Info
```yml
/songs utility info <song name>
```
This is used to get information about a song. Currently, this only outputs the number of notes in the song, and the songs duration.

### Rename
```yml
/songs utility rename <song name>
```
This allows you to rename a song. It will rename it in commands, and also rename the file.<br>
After sending this command, you will have 15 seconds to send the new song name. The new song name should just be sent as a normal chat message.


## Reload
```yml
/songs reload
```
This reloads the plugins configuration, custom registered instruments, and songs. It will not interrupt any currently playing songs.<br>
Use this to update new songs you have added to the servers files.