# Mirabilia

A collection on the phone: pictures, videos, recordings and books, each in
a room of its own, built with the platform and nothing else.

All four rooms are furnished: the pictures show, the videos play, the
recordings play and the books read.

**Names.** A room, a screen or a setting is named for what it holds, not
for what it promises, with one meaning on a phone: the third room holds
lectures and audiobooks as much as albums, so it is not called music. The
poetry is kept for the empty rooms. Names in one row are one part of speech.

| Item | Value |
|---|---|
| Android | 8.0 and later (`minSdk 26`) |
| Package | `io.github.shumtugle.mirabilia` |
| Licence | MIT |
| Dependencies | Android platform APIs only — no AndroidX, no component libraries, no Maven |
| Build | `aapt`, `javac`, `dalvik-exchange`, `apksigner`; one shell script |
| Permissions | folders are opened to it by hand, one at a time; access to all files only if asked for, for books from the whole phone |

## The rules it is built on

The design is a language rather than a set of widgets, and the language
lives in four small classes. Nothing on screen chooses its own colour,
corner, size or speed; it asks one of these.

**Colour** (`Tone`). One seed — a hue and a richness — grows five palettes,
and every role in the application is one tone of one palette. Tone is
lightness as the eye measures it, so contrast between a role and the ink on
it holds for any seed. Where the screen cannot show a colour, richness is
given up, never tone or hue. From Android 12 the seed follows the
wallpaper until a hand moves a dial.

**Shape** (`Round`). Seven steps from square to a full capsule. Anything
that is a button is a capsule.

**Type** (`Letter`). Fifteen rungs: display, headline, title, body and label,
three sizes each. Rooms and screens are named in the platform's serif, as a
museum labels its rooms; everything a finger touches is in the sans.

**Movement** (`Pace`). A block arrives in 460 ms on a curve that starts fast
and settles slowly; the blocks of a screen come 60 ms apart. What leaves goes
in 200 ms. A press answers in 140 ms. Nothing is faster than a press or
slower than an arrival.

## What there is

**The bar.** One field and one round button. The field names the room
you are in, where an address would stand; touched, it takes the room back
to its top. The other three rooms wait behind the round button as capsules
rising out of it, so a room is never crowded by the names of the others.
When the room changes, the old name leaves upward and the new one rises
into its place.

**The rooms.** Each is numbered and named. A room with nothing in it yet
shows a soft shape of its own, which turns by exactly one of its corners
when touched.

**Doors.** Held on the home screen, the icon offers the four rooms, painted
in the colours of the seed and named in the language in use.

**The corner.** The mark of the application stands in the upper corner of
every room, of a book and of a recording, and opens the settings: the front
layer of the home screen's icon — the stem of a letter i, its dot a gold
ball, and a star beside them — laid on a disc of the seed's colour instead
of lilac. Over the paper of a book it is printed rather than lit, as a
printer's device: the same silhouette, its stem cut short, in the ink of the
running head and at the head's right end. The settings open over the book
without closing it, so a book read aloud goes on reading while they are
open, its strip keeping its place in the bar; a recording keeps its strip
the same way. A screen of ours has the round button as its only way out, one
step back at a time; while it is open and nothing sounds under it, the
field names the screen.

**Books.** Folders are opened to the room by hand; each is walked, with its
subfolders or alone — one walk for every room, each taking its share of what
it finds — and whatever is a book stands on the shelf by title,
the last three read first. Nothing is copied. The shelf is written down, so
it appears at once at the next start and is walked again behind the screen.
Books in forms the reader cannot open stand there too; the refusal comes
when one is touched. The folders screen, in the settings, lists the folders
with their names and paths, takes each deep or alone, and gives it back to
the system with two presses; a folder the system took back leaves by itself.
A folder opened inside one already taken with everything under it is
already on the shelf and is given straight back; a folder opened around
others swallows them. A file is known by its place — its volume and path —
and not by the folder it was reached through, so where it was left, its
page and its marks outlive the folder, and a file found twice stands once.
Below them is the other way: every book the phone's index knows of, which
needs access to all files and is asked for there, out loud. A book opens as a page of a printed book:
paper, serif, a justified column, the title above and the number below, and
the leaf turns under the finger. EPUB, FB2, DOCX, ODT, RTF, Markdown and
plain text are laid out this way; PDF and CBZ are shown as the pictures they
are. The middle of the page is one switch: it takes away everything but
the paper — the bar, the phone's own bars, the margins and the corners —
and brings it all back; the menu at the finger offers the same, by name. When the page changes size,
the reader stays on the words they were reading. Held on the page, it
offers: mark the page, the marks, read aloud from the words under
the finger with the page following the voice, turn the pages by themselves
at an unhurried pace, night or day paper, larger or smaller letters, pick
words, and the sound of the page. Picking lays the page out on paper of its
own, with handles and a wash in the seed's accent and a bar drawn by the
application rather than the system's: copy, the whole page, read aloud from
here, share, and the applications that offer to take picked words; a Done
key and the ground around the paper take it away. Each book keeps its page.
The voice lives in the same service as the music, so a book read aloud goes
on with the screen dark, holds for a call or a headphone pulled out, and
answers the headset, the notification and the widgets. While the book is
read aloud, the field gives its place to the voice: a
mark that breathes while it speaks, the words being said in the book's own
face, a thread as long as the book with the voice's place on it, and keys
to go fifteen seconds back, hold or go on, and thirty seconds on; the voice
lands at the head of a sentence, and the page follows the word being said.
The round button puts the voice away first — the strip sinks and the field
rises — and keeps its place in the book; reading aloud there again goes on
from that place. Every return steps back three and a half seconds, so the
thought is picked up and not only the word.
Touching the words steps the voice through five paces. Holding them raises
a dial — every paragraph a tick on a curving drum under a fixed needle, the
page and the minutes to it above — and the same finger, still down, turns
it, to the left for earlier and to the right for later; lifting chooses.
Held at either edge, the drum keeps turning.

**Recordings.** Every recording in the opened folders and everything under them,
folder by folder and never scattered, the three heard last first, each with
the place it was left at. The room shows a folder as one row — the picture
its first recording carries, the folder's name, how many recordings it holds
and which was heard last — and a folder of one recording as that recording.
A folder opens as a screen of its own, at the one playing or heard last:
its name at the head, and under it what every title in it begins with, so
each line says only what is its own; before each line a round with the
recording's number in the folder, lit in the accent for the one playing,
and quiet lines for those heard to their end.

**Folders.** Each opened folder is a card: its name; where it lies — the
phone's storage or a card, and the whole path — so that two folders of one
name are never taken for each other; a capsule of two halves, with
subfolders or without; and the cross that gives it back, which asks once
more. Under them, one card for the whole phone, a line for each room that
can take it — the books, the recordings — each with a switch. A switch says
on or off and nothing else; a choice between two things is a capsule of two
halves; nothing is a chip that names what it will do, which could be read as
what it is. Turning a room's switch on asks the system for the leave it
needs, and the switch turns only once it is given.

**Pictures.** The phone has already listed, dated and shrunk every picture it
holds, so this room walks nothing: it asks the phone's index of pictures,
with the leave to see them — asked the first time the room is entered, and
after that only by the room's own button. Every picture comes first as one
row, then the albums the phone keeps them in — the camera's, the
screenshots', a messenger's — newest first, each a row with its newest
picture, its name and how many it holds, parted by stretches of time. An
album opens as a grid of squares, three across, newest first, its rows made
again as they scroll so that thousands cost what a screenful does; the small
pictures come from the index, a few at a time, and a cell that has scrolled
away before its picture came is not given the wrong one. The string of
pearls runs along the grid with a knot for every month, and its card shows
the picture itself. A picture opens at once over the whole screen — no bar,
no mark, no system bars. A touch there brings the bar of the application
back over it, at the foot where it stands on every other screen: in place
of the field, the picture's label — its number in the album on a plate of
the accent, its name in the book face, and one short line under it: when
it was taken, with what, and how large — and at its end the round button,
writing its mark as the bar rises, the same door every screen of ours is
left by; the mark of the application comes back to its corner with it. The
bar does not go by itself, since the label is there to be read; it stays
while the pictures turn under it, saying each in its turn, and another
touch puts it and the mark away. Pushed upward, the picture gives a little and springs
back, and a sheet rises with what the picture says about itself: its name,
when it was taken, the camera, the lens and its length as a full frame
would name it, the time, aperture and sensitivity, how large it is and how
much it weighs, where it was taken if the camera wrote that down, and where
it lies on the phone — all read from the file and the phone's index,
nothing guessed, and a line with nothing to say left out. Touching the label
opens the same sheet. A finger drags the picture aside and the next comes in
after it; drags it down and it follows, smaller, the dark thinning behind
it. Every way out closes it the same way — the round button, a pull let go
far enough, or back — and the bar goes down with it: the picture sinks, grows smaller, the dark thins, and the grid stands
where it is. Down to close, up to be told. Two touches in quick succession
bring the picture two and a half times closer where they fell, the point
touched staying under the finger, and two more put it back whole; the
second touch held and drawn up or down brings it closer or further by as
much, with one hand. Two fingers bring it closer too, and one finger then
moves over it; two fingers drawn together put it back whole. Since a touch
may be the first of two, the bar waits the moment it takes to tell them
apart before it rises. The small picture shows at
once and the full one takes its place when read, turned as it was taken.
Held, a picture offers to be touched up, and to be shared. The pictures have a card of
their own in the settings; its switch lights the screen at its brightest for
as long as a picture is open, as a print is held up to the window, and gives
the brightness back to the phone when it closes — always, or by the clock,
between two hours set on two drums, over midnight if the first comes after
the last.

**Videos.** The second room is built as the first, from the phone's index of
videos with the leave to see them, asked the first time the room is entered;
what lies in the applications' own folders — a messenger's cache of clips —
is left out. First come the videos stopped halfway, the last watched first,
each with where it stopped of how long; then every video as one row, and the
albums. An album's grid carries each video's length on a dark pill in its
corner. A video opens over the whole screen and plays from where it was
left. Its keys are the recordings' own: the same strip, in the same capsule,
at the foot of the video — its name, how far it has gone and at what speed,
fifteen seconds back, stop or go on, thirty on; the words touched step the
speed; the words held raise the dial of its time, a tick every five seconds
and a taller one every minute, its edges turning on by themselves. The strip
comes with a touch on the video and goes when the video has been left alone
a few seconds; a touch while it stands puts it away. The mark comes and goes
with it, so the settings and the journal are a touch away over a video too,
and the video waits under them where it stopped. A video whose player will
not take another speed says so once, and its strip shows the speed it
really plays at. Drawn down, the video
closes the way a picture does, and so does back. It
asks for the sound and the room of recordings falls quiet; it stops when the
window goes; where it stopped is kept under its place; played to the end, it
starts again from the beginning. Two fingers spread make it fill the screen,
cropping what does not fit, and drawn together show it whole — the choice
kept for the next video. Held, it offers what
can be done with the file itself — the door to a workshop, sharing,
renaming, deleting — and, above them, a frame of it to keep and the hour it
is to fall silent at. Its speed is not there: it is stepped by touching the
words of the strip, and it belongs to the album, not to the video. In an album's grid a line along the foot of a video shows how far it
was watched, and one watched to the end steps back, as a recording heard to
its end does.

**The dial's travel.** The dial of time or of paragraphs — a recording's, a
video's, a book read aloud — is turned on a slide rule, as the string of
pearls is: near where the finger took hold a tick to each small movement,
five seconds or a paragraph; further off faster and faster, so that a little
under half the screen's width reaches the beginning or the end of the whole
thing; the finger brought back brings the needle back. Held at an edge, it
still keeps turning.

**Pace.** One drum for every speed: the words of any strip — a recording's,
a book read aloud, a video's — touched, bring a drum under a needle at the
foot of the screen, three quarters to twice, felt at every step; touched
again, it goes, and it goes by itself a moment after it stops. A pace
belongs to a place, as an order does. The pace of the
recordings is the folder's of the recording playing: a course of lectures kept at one and a half does not hurry the
music beside it, and a book read aloud has a pace of its own. A video's
speed is its album's. The widget shows the pace of what plays and has nothing
to set: it is for the moment, not for keeping.

**Sound**, in the settings: what sounds when the window goes. For now,
whether a video left goes on as sound — playing if it played, paused and
ready if it was paused, so the widget shows it and its key plays it — the sound service
takes it from where it is, with the phone's own sound keys and the widget,
and back on the video the picture goes on from where the sound got to; it
is not put among the recordings heard. The widget's words, while a video's
sound goes on, lead back to the video itself, not to the recordings; while
a book is read aloud, to the book itself at its page — and if that book is
open already, whatever stands over it is put away and it reads on. The
sound's note in the phone's shade leads to the same places. A video taken
back from its sound opens as the sound was: playing, or still. The
widget shows what sounds outside the application; a video on the screen is
in front of the eyes and needs no widget.

**The menu at the finger.** A thing held — a page, a row, a picture, a
video — opens what can be done with it where it was held: a card of rows
that grows from that point as a leaf from its bud, to whichever side there
is room, never past an edge and never over the finger. The rows stand in
groups with a small gap between them: what is about this place, what is
about the whole, and apart, what cannot be undone, in the colour of an
error. The finger that held need not lift: drawn onto a row, it lights the
row with a tick, and let go there, does it; let go without having moved, the
card stays for a touch. The chosen row flashes and the card folds back into
its point. Only verbs about the thing held stand there — never more than
seven rows. On a dark ground a shadow cannot be seen, so the card is lifted
by tone instead — the highest tone of the surface, above every row it may
stand over — and the room behind it goes quieter while it stands, so that
the gaps between its groups show a still room and not words cut in half.
Only one card stands at a time: a new one clears whatever is still on the
screen, even a card still folding away.
How a book looks is not a command but a setting: the paper
opens as a sheet from below — the size of the letters on a drum, day or
night on a capsule of two halves, and three switches: without anything else
on the screen, turning by itself, the sound of the pages. The round button's
own menu still rises from the button: it belongs to the button, not to a
thing held.

**The collection and the workshops.** What is changed here is the collection:
names, places, and small repairs that touch nothing of a file's content — a
picture turned a quarter at each touch by the mark that says how it lies,
not a pixel moved; words picked in a book written into the book's own file
of excerpts, with the page and the day, in a folder of the application's
among the phone's documents, so they are a book themselves. A picture is
never changed either; what is made from it is a copy beside it — see
touching up, below. Heavier work on a file's content — drawing on it,
writing on it, montage, a sound track taken off — is a workshop's, and a
menu at the finger has the door to it — edit in… —
only when the phone holds a workshop for that kind of file: an application
that says it edits pictures, or recordings, or that very type, by name. One
that says it edits anything at all is a workshop for nothing in particular,
and one that only takes a file handed over is a place to share to; neither
stands behind the door. With one workshop the door opens straight onto it;
with several, onto the system's own chooser, those others left out of it.
There is no list of ours. What a workshop makes is found when the window
comes back.

**Touching up.** Held, an opened picture offers to be touched up, and it is
done where the picture lies, without going anywhere to do it: the picture
settles a little higher, onto a stage, and the bar rises under it with three
ways of touching it — light, frame and geometry — and the round button,
writing its mark. Each way is a row of small drawings, one for each thing it
turns, and one drum that turns the one chosen, its name and number shown a
moment over the stage; the panel is the same height for all three, so the
picture never moves when the way changes. Nothing is applied and nothing
confirmed: every turn of a drum shows
at once, on a copy the size of the screen, and the picture in full is worked
once, away from the screen, when the round button is touched. What it makes
is a copy beside the original, in the original's folder where pictures may
be put there, carrying the camera's marks — when, with what, how, where —
and standing as it is seen; the copy then opens in the picture's place, its
label up, and the star that began the touching up comes out over the label's
number, turns an eighth and goes: no note needs to say it was done — and
back from it, the grid stands at the copy's place. The
original is never touched. A quiet cross in the corner, or back, puts it all
away and the picture returns to where it was; if anything was changed, the
cross first opens into words — leave without keeping — and only a second
touch leaves, the words folding away again if left alone.

The light is one drum from the picture as it is to a sheet of paper made
white, eight stops along one road. Along it the black and white points are
read from the part of the picture in the frame, more of the extremes let go
the further it turns; a middle that has wandered from grey is brought back
by a curve; and from the middle of the drum on, the brightest reds and blues
are balanced so that white is white — a page photographed under a lamp comes
out as paper. Should a picture be too light for its black point to come
down, the stop gives way by itself rather than break it. Everything a stop
does folds into one table for each channel, so each pixel is touched once.
It is arithmetic on the picture itself — no model, no network, and the same
answer every time. What the automatic drum finds it writes into brightness,
contrast and warmth, which have drums of their own beside it, with
saturation, a hundred either way; the hand can take up where the automatic
left off, and a new turn of the automatic drum writes them afresh. Colour is
mixed first, as the display's own filters mix it, and the rest folds into
the tables. Last in the row is sharpness, a darkroom's unsharp mask: the
picture set against a softened copy of itself and the difference added
back, the least differences let alone so grain does not turn to noise, and
the softening as wide on the kept copy, measured on the picture, as it was
on the screen. While a finger rests on the picture, the light shows as it
was.

The frame stands still on the screen and the picture moves under it: closer
on a drum or with two fingers, up to four times, and led by one finger. What
is in the frame is what is kept, so there are no handles at its corners to
find. The other drum gives it a shape — as it is, free, square, and four by
five, three by two and sixteen by nine each either way round, standing or
lying, chosen rather than guessed from the picture — and the frame flows
into the new one. A free frame shows a short bar at each edge and can be
taken by any edge or corner: while it is held the picture stands still and
the edge follows the finger over it, stopping at the picture's own edge;
let go, the frame fits its room again, flowing. The last
drum is the size of the copy: a quarter or a half, for sending; as it is;
or two, three, four times larger, for printing, the size in pixels said over
the stage as the drum turns. Smaller is made by halves, each filtered, so
no pixel is skipped over; larger by the three-lobed windowed sine, six old
pixels weighed each way for every new one. Larger invents nothing: what was
not in the picture is not in the larger one either, only drawn smoothly —
which is why the drum stops at four, where smooth still looks like the
picture and not like paint, and why a larger copy is held under twenty four
million pixels.

The geometry straightens: the angle, by the degree, forty five either way;
the lean of the picture up and down and side to side, as a wall
photographed from below narrows towards its top; and the lens, whose bend
swells or sinks the middle while the middles of the edges stay. The picture
is drawn through a fine mesh that the display draws in one pass, so it
bends as the drum turns. Lines in thirds stand in the frame to straighten
against. A straightened picture no longer fills its rectangle, so the frame
shrinks until it lies wholly on the picture again — once, for all of it
together, rather than once for each, which would pay for the corners three
times — and a finger leads the frame only as far as the picture reaches,
sliding along its edge.

**The camera's door.** Nothing of a camera is built here; the phone has one.
In the rooms of pictures and of videos, the round button's capsules end
with one more, apart and in the accent, nearest the finger: take a picture,
or a video. And the application's icon, held on the home screen, has the
same door first among its own, so a picture can be taken for the collection
without the collection being opened. A place is made in the application's
own folder first and the camera writes straight into it; the place is
written down rather than held, so a camera that outlives the window still
finds it, and a place the camera left empty is taken away, so no empty
picture ever stands in the collection. A picture taken opens in the
application's album and straight into touching up, the automatic light
already on its first stop, the one for photographs: one touch of the round
button and it is kept, a little better than it came out of the camera. A
video taken opens to be watched.

**The journal.** What the application did, in the settings, to be sent when
something went wrong: the hour apart from the words, the subject of each
line lit, and the runs of the application parted by a line of their own,
since a phone that lets the application go while a camera is in front takes
the journal with it and the next start reads it back. A drum turned forty
times says the same thing forty times; such lines are folded into one, with
the count of how often it was said. Both keys still take the whole of it,
unfolded, to the clipboard or to anyone who will read it.

**Sleep, and again.** Whatever sounds can be set to fall silent: held, a
recording, a video, or a page being read aloud offers the sleep, and a drum
of minutes — five to ninety — with never at one end and the end of what is
playing at the other. The hour is one for everything that sounds, so a
recording in the background and a video on its own screen watch the same
one; a video watched on its own screen is a thing that sounded too, so the
home screen shows it once it is closed, and its key opens it where it
stopped rather than playing whatever was heard before it. When the hour
comes the sound is let down over a few seconds and then held,
so it ends rather than stops; what was playing keeps its place and is there
in the morning. A recording's menu also turns the repeat: nothing, this one,
or the folder — the folder beginning again from its first recording when its
last one ends.

**Touching up a recording.** Held, a recording in the commonest compressed
form offers to be touched up, as a picture does, in two ways. A piece: the
whole recording is a line, and two discs on it, A and B, are taken by the
finger and dragged; under them a drum turns the one in hand frame by frame,
the smallest step the recording has, some twenty six thousandths of a
second. The piece between them plays round and round while it is set —
setting A plays from A, setting B the last two seconds up to it — so the
marks are a loop to learn by as well. The round button keeps the piece as a
new file beside the recording, cut by copying its frames byte for byte:
nothing decoded, nothing encoded again, nothing lost; and the piece is heard
at once. Its words: the name, who, the album, the number and the year, read
from the recording's tag — Cyrillic in the old eight-bit table read as such
— and written into the piece, or, when only the words were changed, into
the recording itself: never over it, but as a new file beside it put in its
place, the old one let go only after. Everything else in the tag, a cover or
a lyric, is carried over as it was.

Over the words stands the cover: the picture the recording carries, and the
way to give it another from the collection, rising as a sheet — the covers
first, those taken out of other recordings, then every picture. A picture
chosen is cut square from its middle, no larger than a thousand pixels a
side, and written in place of every picture the tag held.

**Handing the work on.** A touch-up can go straight to another application
without being kept here first: in the corner opposite the way out stands the
sharing mark, and a touch on it makes the picture in full — frame, geometry,
light, size, sharpness — writes it into the application's own scratch and
offers it to whoever will take it: a message, a note, a workshop. Nothing is
added to the collection, the touch-up stays open, and what was handed on can
still be kept, changed further, or let go. The scratch is the application's
alone: another application is given leave to read one file of it and nothing
else, and what is not taken is swept away within half a day.

**Covers.** A book's excerpt is its words, a video's is a frame, and a
recording's is its cover. Held, a recording offers its cover to the
collection, and a folder of recordings, held, offers all of theirs at once.
They go to one folder among the pictures, Pictures/Mirabilia/Covers, so they
stand together in the room of pictures as one album: a wall of the music's
own faces. Each is named for what it shows — who, the album, the year — in
whatever language the recording names them, since the words inside an
image's own marks are Latin letters only by their rules; the name is the
card. One album gives one cover however many recordings carry it. Only what
the files hold is taken; nothing is looked up anywhere.

When the words are wanted as well, a recording or a folder writes its tags
down: a card of plain text for each album among them, named as the album's
cover is so the two are found by one name, in Documents/Mirabilia/Covers
beside the excerpts — who, the album, the year, the genre, the tracks in
their order with their lengths and the whole length, the sound's form, and
where it came from. The card's own words are English, as the application's
files are; what the tags say stays in their own language. Being plain text,
a card is a book too, and stands on the shelf.

**A frame of a video.** Held, a video offers a frame of it to keep, and the
frame is chosen, not guessed: the video stops, its keys go, and a drum turns
under it frame by frame, a tick for each, the seconds named, the video
following it to the very frame under the needle — not the nearest frame it
could start from. The bar says the time and which frame of the second. The
round button keeps that frame as a picture; the screen blinks once, as a
shutter would, and the chooser stays for the next. A cross, or back, goes.
A clip of up to twenty seconds is on the drum whole; a longer video, ten
seconds either way of where it stood.

A touch that begins at the very top of the screen belongs to the phone,
which pulls its shade from there; it never pulls a picture or a video down.

**Looking and naming.** The field at the foot of the screen, touched while
the room is scrolled down, takes the room back to its top; touched at the
top, it takes words: what to look for in this room, by the name of a file or
of its folder — every word must be there, and a letter written two ways is
one. The room then shows what was found, in its own rows, and back from
what was opened it shows it still; the round button or back puts the
question away. A row, a picture or a video held offers, beside sharing, to
be renamed — in the same field, its ending kept, and everything kept under
its old place moving with it — or deleted, the capsule asking once more
unless the system is about to ask itself. While a name is being written the
round button wears the mark of done and keeps the name, as the keyboard's
own key does; back puts the name away unchanged. A name already taken in
the folder is said to be taken. A file renamed goes by its new name in the
lists of what was read, heard and watched last, as it does on the shelf; a
file deleted leaves them. A file that is no longer where it was known to
lie — moved or renamed by another hand — is said to have gone, and leaves
them too, rather than being offered again under a name that is not there. The file itself is changed where
the leave for every file allows; a file of an opened folder through the
folder, which is now opened for writing as well; a file known only from the
phone's index through the index, the system asking first. A folder opened
for reading only is not left to be found again by hand: the system's folder
picker opens where the file lies, asking for writing as well, and the change
waiting on it is finished when the folder comes back. Every change, done or
refused, leaves a line in the journal.

**The string of pearls over a grid.** A grid has no selection of its own
while a finger is on the screen, and asking it to place a row it has not
selected sends it to the very top instead — so the pearls moved and the
pictures did not. The row is chosen rather than placed, which a grid
understands with no selection at all, and the last hair — standing the row
where the pearl is rather than at the top — is given afterwards by moving
the rows themselves.

**An album's face.** A cover is the album's newest picture, and a phone has
not always made a small one of it yet: then it is made from the picture
itself, which takes a moment. Until it comes the album stands by its
initial on a plate of colour, as a folder of recordings does, and the cover
fades in over it; an album whose picture cannot be read keeps the initial
rather than an empty grey square. A few covers are made at once, so a room
of albums fills in at once rather than row after row. A face is kept under its album
rather than under the picture it was made from — an album's newest picture
changes whenever one is taken, and the room would come back with letters
where faces stood a moment ago — and apart from every other picture, so
that walking a grid of three thousand cannot push it out. The newest wish
for a picture is granted first, so a room one returns to does not stand in
the queue left behind by that grid.

**The shelf hands out its rows.** A shelf of a few thousand books is not
built to be looked at three at a time: its rows are made as the eye reaches
them and taken back as it leaves, with the room's head standing above them
as part of the same list. The string of pearls reads the shelf itself rather
than the rows, since most of them do not exist at any moment; it still
carries a title to every pearl and brings the shelf to it. Under the three
books being read stands the whole shelf, named as such, so that the first of
it is not read as a fourth of them.

**Shelf and stream.** A room made of chosen folders is a shelf and stands in
its own order: books by title, recordings folder by folder. A room fed by
the phone's index is a stream: newest first, by the day each thing came,
parted by quiet names of stretches of time — today, this week, this month,
this year, earlier — and in the room of recordings a folder that came at
once is one row, dated by its newest file. Each room has its own index and
its own leave: the books take every file the phone holds, with the leave for
every file; the recordings take what the index of audio knows, with the
leave for audio alone, less ringtones, notification and alarm sounds, the
recorder's notes and whatever lies in the applications' own folders, where
messengers keep voice messages. The recordings' index is off until it is
turned on.

**A folder's order.** At the head of a folder's screen a capsule of two
halves: by number, as the names read, or by time, in the order the files
came, for a folder whose names do not tell. The choice is kept for that
folder, by its place, and the player follows it.

**Sharing.** A row held, a page held, a picture or a video held — the menu
that grows from the finger offers to share. The file goes to the system's
chooser — a chat, a mail, a drive — by its own address with leave to read
it, which lasts as long as whoever takes it needs it; nothing is copied. A
recording shares itself from its own screen as well.

**The ruler.** A long list — the shelf, the room of recordings, a folder —
has a string of pearls down its right edge. While the list moves, one pearl
rides on a faint thread where the eye is in it, and fades when the list is
still. Held, the pearl turns gold — the dot of the application's i — and the
string runs out of it to both ends of the screen: the first row at the top
clasp, the last at the bottom one, a pearl for every row between. The gold
pearl follows the finger along the string and points at the row whose
pearl it is on, a card by it naming the row and where it stands, and the
list moves so that row stands beside it. The string is laid out where it
was taken hold of and magnified there, as under a loupe: around that place
the pearls are large and apart, a row to a small movement; toward the
clasps they grow small and crowd, tens and then hundreds to the same
movement, as a slide rule's scale crowds toward its end. The spacing is
fitted to the screen, so the ends are always at the clasps; a list short
enough is spread evenly. To go finely somewhere far off, the finger lets go
and takes hold there. There is no panel behind the string, only a shade
rising from the edge. It is meant to be played with: the gold pearl has
weight and follows the finger a moment behind, the pearls it passes swell,
and the card — large, over the row itself, with the row's cover or its
initial or number — turns a leaf for every row, the new one coming in from
the side the finger moves to. Where the list changes its section — a letter of the
shelf, a chapter of a folder — the thread has a knot, and the sections
before and after the one in hand are named at theirs; a list whose every
row would be its own section has none. Let go, the list stands at the row,
and the row answers as if touched.

**Words that moved on.** A module keeps, with every word, the English it
was translated from; when the English under a word changes, the language
screen says how many words are out of date, so a rename is not hidden
behind a full count. A folder that holds the
platform's mark against being scanned for media keeps its recordings, and
everything under it, out of the room; its books stay on the shelf. Within a folder the order is the folder's own: the
names read as a person reads numbers, the second before the tenth; where no
name carries a number and every file carries the number of its track, the
tracks. A recording plays in a service
that outlives the window: a call takes the sound away and gives it back, a
headphone pulled out stops it, the keys of a headset and of the
notification — fifteen back, hold or go on, thirty on — reach it from
anywhere. Each recording keeps its place, written every five seconds and at
every pause; coming back to one, after a pause or another day, steps back
three and a half seconds, and one nearly finished starts over. The
recordings follow one another within their folder, and the sound stops at
the folder's end: a book of lectures ends where its last lecture does and
does not run on into the next book. A file named only by its number takes the
name of its folder, since that is where an audiobook keeps its title.
Touched, a recording plays and its own screen opens over the room — its
cover and its tags, the strip below — and the round button goes back to
the room while the sound goes on; another application can hand a recording
over and it opens the same way. In the room, the bar gives its field to the
same strip the voice of a book has — the breath, the name, a thread, the
time and the pace — and holding its words raises the same dial, in time: a
tick every five seconds, a taller one every minute.

**Widget.** One, for the home screen, over one account of what sounds — a
recording, or a book read aloud: what it is, where it is, how far, whether
it sounds. It is a single picture in the application's language: the
room's numeral and a breath, the name in the book face, the whole of it as
a scale with the part heard in the accent, a key that holds and goes on,
and two small discs for back and on; a touch on the words opens the room.
Its keys go to the sound service while it lives — to the voice if a book
is read aloud, to the player otherwise — and, when it does not, open the
window with their wish: the book read last is opened again at its page and
the voice goes on, or the recording heard last plays. Its look is chosen in the settings,
on a screen with one picture: the widget itself on a wallpaper grown from
the seed, standing still at the head while the choices scroll under it,
with a recording or with a book read aloud. The choices are plain, each
named above its row: the two grounds — the seed's own container, or glass
over the wallpaper with everything on it in warm bone and brass — the gold
ball and the record, the drum of ticks and the single thread, each pair
a capsule of two halves with the thing drawn small in each and a fill that
slides to the one chosen. The depth of glass is a drum under a needle, six
steps from light to dark, turned by the finger and ticking at every step.
Whatever is chosen answers at once in the picture at the head. The widget
moves with the sound every few seconds.

The record wears the cover of what sounds, when there is one: the picture
inside a recording, or the cover of the book read aloud, printed over the
whole disc in the two inks of the ground rather than in its own colours,
with a small disc of the ground in the middle, as a record's label, half
clear so that the face a cover keeps in its middle shows through, for the
mark. With no cover it is a plain disc, and the widget keeps its shape
either way. Covers are fetched once, away from the screen, and kept on disk,
so drawing the widget opens no file. The gold ball wears nothing: it is the
dot of the application's i.

**Previews.** A book's row wears its cover over its letter, and a
recording's round label the picture it carries: the storage provider is
asked first, and otherwise the file is opened — the tags of a recording,
the first page of a pdf, the first leaf of a comic, the cover of an epub or
an fb2. Only rows the eye can see are fetched, by three hands at once, and
what is fetched is kept in a cache sized to the phone's memory.

**Journal**, in the settings: what the application did, the last fall
included, to copy or send when something goes wrong.

**Colour**, in the settings, first, with the widget beside it. A specimen of the colour roles and two dials,
hue and richness, whose tracks are painted with what they choose. Every
movement regrows the whole scheme in place.

**Language**, in the settings. The interface is English; every other
language is a module — one text file. `modules/template.txt` is the empty
form, `modules/ru.txt` a complete one. Load a module from the language
screen; a word left empty stays English.

## Building

```sh
KEYSTORE=../keys/your.keystore KSPASS=<password> SDK=$HOME/sdk/android-33.jar \
  sh build.sh mirabilia-<version>
```

`build.sh` compiles the sources against `android.jar`, packs the resources
with `aapt`, converts with `dalvik-exchange` and signs with `apksigner`. The result is
`out/mirabilia-<version>.apk`. No Gradle, no daemon, no network.

## Layout

```
AndroidManifest.xml
build.sh            the whole build
res/                the icon, the window theme
src/                the application, one package
modules/            language modules
```

## Licence

MIT. See `LICENSE`. Every mark and shape is drawn in the source.
