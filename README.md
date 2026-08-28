# Noteblocks Live
![Available for paper](https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/supported/paper_vector.svg)

This plugin allows for the playing of Midi, Noteblock Studio, and custom-made songs live using noteblock sounds. While it is fun to use for playing custom music, it is most useful as a tool for other projects to play music.

### 🚨 WARNING - (EARLY DEVELOPMENT)

#### This plugin is in early development!! There are still many features planned, and there may be some bugs. I have done a lot of testing, but am only one person. Please report any bugs, and feel free to request features.

### Requirements
#### ⚠️ FOR BEST FUNCTIONALITY, A RESOURCE PACK IS REQUIRED. BY DEFAULT THIS WILL BE PUT IN THE PLUGINS FOLDER, AND LOADED FOR PLAYERS FROM GITHUB. MANY SONGS WONT SOUND RIGHT WITHOUT IT.

### ✅ Features and utility
 - Play custom songs live in game
 - Add songs to the /MCMidi/songs folder
 - Songs can be nbs or midi files, and if you have a custom song made for the plugin, can use YML or TXT files.
 - Songs can be in folders, the plugin will still be able to read them.
 - Use /songs reload to update the songs loaded on the server.
 - Songs can be played from an entity, from a location, or directly to players.
 - Songs can be queued, looped, and skipped.
 - Each song player can have its volume updated individually.


### 📬 Reporting issues
To report bugs, ask for features, or seek help in other ways, feel free to join the [discord](https://discord.com/UPDATETHISLINK), or create an issue on the [github](https://github.com/SillyLittleDev/MCMidi). <DONT FORGET TO MAKE A DISCORD BEFORE PUBLISHING YOU LAZY BUM><br>
Also, development of this plugin has been spread out over long periods of time. As such, some explanations may have mistakes, or be less than would be hoped for. Please reach out with any issues or gaps in any of the docs made for this plugin.

### 📁 Currently supported file formats
 - Midi
 - NBS - NBS songs marked as noteblock compatible will work with the resource pack disabled. Otherwise, the resource pack will be needed to function.
 - YML - Uses the custom music format below. Each entry will be read as a string, with the location being the name of the song.
 - TXT - Also uses the custom music format below. Expects a single string in the file. Song title will be the file name. Also works with the file extension .nbl

### 📝 Custom format
The custom format used for writing music is a simple text format. Different parts of each note are separated by semicolons, and notes are separated by spaces.<br>
The format is as follows:<br>
[instrument];[octave];[tone];[sharp];[postPause];[volume]<br>
or<br>
[instrument];[octave];[tone];[sharp];[postPause]<br>
The last semicolon along with volume is optional, and will by default play at full volume.

 - The instrument is just the instrument name in game.
 - The octave is measured as a shift from the default. An octave above normal is 1, an octave below is -1.
 - Tone is the letter tone of the note.
 - This is a single letter for if the note is sharp or not. s for sharp, and f (or any other letter) for not sharp.
 - The post pause is the time in nanoseconds until the next note.
   - 2 notes played at the same time will have a 0 as the post pause for the first note listed, and the pause for the second one being the time to the next note.
 - Volume is a number from 0 to 1 denoting volume. 1 is full volume, 0 is off. 

Example - An A note on the harp: harp;0;a;f;0<br>
Example - An A note on the harp at half volume: harp;0;a;f;0;0.5

This is the standard usage for the default minecraft instruments. Additional sounds are available by using the full sound key:<br>
Example - The default fire rocket blast sound (default sound is f sharp): minecraft:entity.firework_rocket.blast;0;f;s;0<br>

Using sound keys for minecraft, or other sounds in a resource pack cannot use the prefix "custom:", as that prefix is reserved for registered custom instruments.

#### Custom Instruments
Custom instruments are supported. Custom sounds can be used, as shown above, but custom instrument implementation can also be used.<br>
The custom instrument tools are only meant for very specific usage, and has undergone far less testing than everything else. It is suggested to just use the above full sound name setup in songs.

##### WARNING: THE CUSTOM INSTRUMENT SYSTEM WAS MADE AS A TEST. IT IS UNDER TESTED, LIKELY PRONE TO ISSUES, AND MAY HAVE ISSUES. PLEASE EXPECT ISSUES. PLEASE REACH OUT WITH ANY BUGS.

Custom instruments allow for more advanced musical instruments in the game. While it adds the normal functionality of sounds with an extended range, it also supports things like held notes, allowing for sounds with dynamic lengths.<br>
Learn more about how the custom instrument implementation works in [INSTRUMENTS.md](INSTRUMENTS.md)

### ☐ Planned features
 - Add music fading / transitions
 - Add a now playing display option which only shows at the start of each song.
 - Add more in depth playlists
 - Add pause and unpause