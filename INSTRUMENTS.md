# Custom Instruments
Custom instruments was initially made as a test for midi, and hasn't really been updated since then. It likely wouldn't see much use in other formats, but I feel it is worth noting.<br>
These instruments allow for custom sounds to be associated with individual specific midi channels, programs, or even notes. It can be used to expand the playability of midi, but also allows for held notes.<br>
Custom instruments expect to have shifted versions of the audio. This is versions of the sound moved up and down octaves to help it function well with Minecraft's limited sound engine. While it is recommended to have all 6 shifts, you only actually need to have ones which will be used by the music you play.<br>

## Wording
I am not a musician. I am not super familiar with most musical concepts. I learned music theory, midi formats, and other concepts for the purpose of making this plugin. As such, some of the ways I refer to things may be irregular (honestly, I haven't got the slightest clue).<br>
So for the purpose of clarity, I will explain what I mean with some of the things I say here.<br>

#### Held notes / looping audios
This is used when I am talking about notes or audios which are not of uniform length. All audios, and importantly for this noteblocks, just have an audio file which plays when it is supposed to.<br>
The limited audio can cause some issues in music. Many instruments can be played in such a way to hold a note for a longer time, and that is the concept I am referring to.<br>
Held notes and looping audios refers in general to all notes which are not of uniform length, and as such, have a section in the middle which loops to hold the note.

#### Shifts
Due to Minecraft's limited audio engine, audio can only be shifted one octave up, or one down.<br>
For this purpose, this plugin uses shifted audios. This is implemented automatically for Minecraft's vanillas instruments in the default resource pack.<br>
The shifts are each moved by 2 octaves up or down. This is to allow for solid coverage of notes. Shifts are marked by whole numbers, indicating 2 octave shifts either up or down.

## Usage
Custom instruments are stored in the MCMidi plugin folder inside the custom-instruments folder. These files must be in the yml format.<br>
Non-Midi files can use custom instruments as well using the prefix "custom:". This is not recommended, as the text file format is capable of playing any sound in a resource pack just by using its full key.

## File format
The below file is an example custom instrument file. It is for an example piano instrument, which supports held notes. <br>
The min and max-duration-ns is only used for looping audios / held notes, and can be left out of custom instruments which do not use it.<br>
#### By default, you should assume your custom instruments do not support looping / held notes. If you don't know if you are using it, you probably arent.
The match values are used to select what sounds in midi files are replaced with this custom instrument.

### Looping audios / held notes
Held notes use additional information. This is mostly timings for when the audio should loop, when it should transition from attack, to the loop, to the release.<br>
All of these are measured in nanoseconds. The shifts are for the shifted versions of the audio. As audios often change in length when pitched up or down, it is expected that all audios at different shifts will not be of uniform length.<br>
While data is needed for all shifts, if your songs do not use those shifts, you can ignore them. Shifts go intentionally farther than is almost ever useful to prevent songs from having issues.

### Example Custom Instrument File
```yml
id: piano
namespace: custom-sounds
instrument: piano
looping: true

priority: 100
reference-c: 60

# These are for looping audios / held notes.
# Delete them to default to ignoring length.
min-duration-ns: 1000000000
max-duration-ns: 999999999999999999999

match:
  programs: [0, 1, 2, 3]
  channels: []
  notes: []

# This section is only needed for if the custom sound includes looping / held notes.
timings:
  default:
    attack-ns: 250000000
    attack-overlap-ns: 50000000
    loop-ns: 1000000000
    loop-overlap-ns: 50000000
    release-ns: 250000000
    release-overlap-ns: 50000000

  shifts:
    "-3": { attack-ns: 250000000, attack-overlap-ns: 50000000, loop-ns: 1000000000, loop-overlap-ns: 50000000, release-ns: 250000000, release-overlap-ns: 50000000 }
    "-2": { attack-ns: 250000000, attack-overlap-ns: 50000000, loop-ns: 1000000000, loop-overlap-ns: 50000000, release-ns: 250000000, release-overlap-ns: 50000000 }
    "-1": { attack-ns: 250000000, attack-overlap-ns: 50000000, loop-ns: 1000000000, loop-overlap-ns: 50000000, release-ns: 250000000, release-overlap-ns: 50000000 }
    "1": { attack-ns: 250000000, attack-overlap-ns: 50000000, loop-ns: 1000000000, loop-overlap-ns: 50000000, release-ns: 250000000, release-overlap-ns: 50000000 }
    "2": { attack-ns: 250000000, attack-overlap-ns: 50000000, loop-ns: 1000000000, loop-overlap-ns: 50000000, release-ns: 250000000, release-overlap-ns: 50000000 }
    "3": { attack-ns: 250000000, attack-overlap-ns: 50000000, loop-ns: 1000000000, loop-overlap-ns: 50000000, release-ns: 250000000, release-overlap-ns: 50000000 }
```