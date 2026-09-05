# Aletheia

*ἀλήθεια* — truth as **un-concealment**, which is the whole job: a client-side Fabric mod for
**Minecraft 26.1.2** that surfaces what Telos Realms buries. The chat lines that matter become
on-screen titles, the server's own HUD numbers become readouts you can put where you want them, its
boss bar becomes one you can move, shape and colour by what the fight is doing, and its wordier
messages get cut down to something you can read at a glance.

Settings live in **YetAnotherConfigLib** — `/aletheia` on its own opens them, as do `/telos`, `/ale`
and `/a`, and so does the settings button on the mod list if you have Mod Menu. The aliases are redirected at the real command rather than built twice, so they cannot drift;
everything else is a subcommand of any of them (`/a hud`, `/a pots`, `/a status`).

The first tab is **Start here**: what the mod does, the two switches worth setting on day one, and a
button into the readout editor. Every option on every other tab carries a description in the panel
beside it — there are close to two hundred of them and most name something *Telos* does rather than
something Minecraft does, so a name on its own ("Only for mobs this good", "The bar's fill picture
contains") is not enough to go on.

*Options → Controls → Aletheia* has a key for the editor and one for the settings screen. Both ship
**unbound**: a mod that claims a key on install is a mod that quietly breaks whatever you already had
there.

## What it does

| In chat | On screen |
| --- | --- |
| `Neo-Eden acknowledges your persistence. (175/UNDEFINED)` | **175/???** |
| `Your persistence has been recorded. (259/265)` | **259/265** |
| `[player] is attempting to solve the edenic light puzzle!` | **PUZZLE STARTED** |
| `[player] Has solved the edenic light puzzle!` | **PUZZLE SOLVED** |
| `[player] is attempting to defeat the edenic warriors in the edenic barracks battle room!` | **BARRACKS STARTED** |
| `[player] has defeated the edenic warriors in the edenic barracks battle room!` | **BARRACKS DONE** |
| `肔 ᴄʜᴇʀᴜʙɪᴍ  Enough!` | **ENOUGH!** — subtitle `STOP ATTACKING` |
| `肔 ᴄʜᴇʀᴜʙɪᴍ  Silence!` | **SILENCE!** — subtitle `STOP ATTACKING` |
| `[Dreadwood Civilian] [player] is attempting the Colour Room!` | **COLOUR ROOM** |
| `[Dreadwood Civilian] [player] has completed the Colour Room!` | **1/2** — subtitle `COLOUR ROOM DONE` |
| `[Herald] The torch burns bright.` | **HERALD SPAWNED** |
| `(1/5) Cog Stabilisers destroyed` | **1/5 COG** |
| `(5/5) Cog Stabilisers destroyed` | **COG ACTIVE** |

The score title is driven by the trailing `(current/total)` counter rather than by any one sentence,
so the phrasings not listed above still work. While the dungeon reports the requirement as
`UNDEFINED`, the total is replaced with `???` (configurable).

Each room announces itself twice — once when someone walks in ("is attempting to…") and once when
they finish it. The two are separate titles with their own text, colour and sound switches, so a run
can be followed without reading chat; the start titles are yellow by default and the finish ones
green.

Cherubim's shouts get their own, longer, on-screen time and a sound by default, since those are the
ones you actually need to react to.

Titles are drawn **flat** — vanilla puts a drop shadow behind every letter of a title, which is off
here by default and comes back with **Titles → Drop shadow behind text**. It covers the mod's own HUD
readouts as well, and any title on screen: the shadow is hardcoded inside `Gui.extractTitle`, so this
is a redirect of the draw call rather than a setting anything passes down.

Two other things end in a counter and are never treated as dungeon score: realm boss kills
(`... has been defeated. (4/10)`) and the network-wide transcendence broadcast
(`[Singapore, Hub-1]弒聖 [player] Has just fully transcended Assassin! (1/6)`, where the counter is
how many classes that player has transcended). If something else on the server starts setting off the
score title, add a word from it to **Ignore lines containing** in the settings.

## Dreadwood Thicket

The Thicket announces each room twice — `is attempting the Colour Room!` on the way in, and again
when it is finished — for the Colour, Wave, Demon and Bullet Hell rooms alike. The room name is
captured rather than listed, so one nobody has written down yet still announces itself.

Both titles are templates: `{room}` is the room's name in capitals, `{player}` who it was, and
`{done}`/`{total}` how far through the run you are. So the finish title is `{done}/{total}` — **1/2**,
then **2/2** — with the room in the subtitle.

**The count is kept by room name, not as a tally.** Nothing in either line says which room of the run
it is, and a room announced twice — a retry, or the server repeating itself — must not advance the
run. Four things end a run:

| | |
| --- | --- |
| Leaving the dungeon, or entering it | count dropped |
| Changing server, or disconnecting | count dropped |
| A completion arriving when the run is already full | that is the next run's first room |
| No Dreadwood chat for 15 minutes | count dropped (configurable) |

Note the first: *leaving* the dungeon, not changing dimension. The rooms of a Telos dungeon are
separate dimensions, so the reset Nature's Gift does below would wipe the count halfway through a
run. Instead the count survives any move whose destination is still a dungeon — recognised by the
*shape* of the id, `telos:dungeon/2`, and explained under [the run clock](#knowing-you-are-in-a-dungeon).
`/aletheia dreadwood` prints the count, the dimension you are in and why it was last cleared;
`/aletheia dreadwood reset` clears it by hand.

Both halves are the real wording — `is attempting the X Room!` and `has completed the X Room!` —
with `cleared`/`finished` accepted as slack against a re-word. It is the room name and the word
"Room" that identify the line, so a new room type needs no code change.

## Shadowlands

The mobs announce themselves — `[Reaper] The spectres watch closely.`, `[Herald] The torch burns
bright.`, `[Warden] The flag flies once more.` — so **HERALD SPAWNED** and so on. `{mob}` and
`{line}` are the placeholders; putting `{line}` in the subtitle shows what it said underneath.

The tag finds the line; the **sentence** decides whether it is a spawn. They talk at other times too —
`[Reaper] So we shall persist in spirit.` is the Reaper *dying*, `[Defender] Master, I require
strength.` is it asking for something — so each mob's spawn line is listed in `SPAWN_LINES` and
anything else it says is dropped. The comparison ignores punctuation and casing.

The Defender's spawn line is **literally `...`**, which folds away to nothing, so it is matched by the
*absence* of words rather than by containing anything — every other line would "contain" an empty
string. That also makes it indifferent to whether the server writes three dots or one ellipsis glyph:
neither survives the fold, and nothing it says later can look like it.

The four names are spelled out in the pattern rather than taking any `[Tag]`, which would swallow
rank prefixes and guild chat — a fifth announcer is a one-word change in `NeoEdenParser`.

## Boss phases

The endgame fights are not damage races — they are scripted, and every phase change lands at a
**fixed percentage of the health bar**. The bar is a number the client already has, so it can say
what is coming before it arrives:

| Fight | Threshold | Called |
| --- | --- | --- |
| Cherubim | 60% | **BLACK HOLE** |
| Cherubim | 20% | **DESPERATION** |
| True Seraph | 50% | **QR CODE** |
| True Seraph | 20% | **DESPERATION** |
| Seraphim |WALLS** |
| True Ophan | 60% | **CLOCK** |
| True Ophan | 15% | **DESPERATION** |
| Ophanim | 85%* | **WALLS** |
| Ophanim | 60%* | **CLOCK** |
| Sylvaris | 75% | **SHULKER** |
| Sylvaris | 25% | **ARROWS** |
| Raphael | 75% | **MEMORISE** |
| Raphael | 50% | **BELL** |
| Raphael | 15% | **DESPERATION** |
| Voided Omnipotent | 85% | **CHASE** |
| Voided Omnipotent | 30% | **BELL** |
| Voided Omnipotent | 15% | **DESPERATION** |

The ordinary Seraphim and Ophanim run the same set pieces as the True versions they gate, so they are
called too. \* their thresholds are **taken from the True version** — the phase is known to be there,
the percentage it starts at is not; correct them in `BossPhases` if the warning lands early.

Voided Omnipotent (Tenebris, the dungeon Raphael's Chamber drops the key to) has six phases, of which
three change what you have to do. The wiki names two of them itself — Chase and Bells — and describes
the last as the Void erupting, with lasers and three eyes to right-click; that is called
**DESPERATION** here to match the other fights' final phase. Its 65% mark is the snakes/pillars/black
holes cycle and is left out for the same reason Sylvaris's 50% is. Note the pack ships **both**
`omnipotent.png` and `voided_omnipotent.png`, and the first is a substring of the second — only the
voided fight is listed, and a test pins that the plain bar stays unmatched.

A phase is a name and nothing else — if you are in the fight you know what it means, and a line of
instructions under it is one more thing to read at the worst possible moment.

Titles fire at the threshold **plus a lead** — the black hole at 60% is called at 63% — so the words
are up while you still have somewhere to stand. The lead is in percentage points rather than seconds,
because damage is what moves the bar and no clock can predict that. Set it to 0 to be told exactly on
the threshold.

There is also a readout, `Cherubim 63% → BLACK HOLE`, draggable like the others, which reddens as the
threshold comes up so it warns even with the titles off. `/aletheia test phase` walks through every
phase of every fight.

Only transitions that **change what you have to do** are listed. A boss swapping attack sets needs no
announcement — you can see that. The Shadowlands Defender is deliberately absent: it is a two-phase
fight but nothing documents at what percentage it turns, and a title at the wrong moment is worse
than no title.

**The bar has no name.** Lotil's boss bar is a single private-use codepoint drawing
`telos:glyph/bossbar/lotil.png` and no text at all, so reading its characters gives an empty string —
which is why the first version of this never fired. The fight is identified by the *texture* behind
that glyph, read out of the pack's font definition by `HudGlyphs`, exactly as the potion counter is.
Nor can the server's HUD bar be told apart by *having* a resource-pack font, since the real boss bar
has one too; only a bar naming a known fight is followed.

That also settles the naming: the pack calls the wiki's True Seraph and True Ophan
`hardmode_seraphim` and `hardmode_ophanim`, which are the keys used here. `seraphim` is a substring of
`hardmode_seraphim`, so the hardmode fights are matched first — get that order wrong and every True
fight is called at the ordinary fight's percentages. A test holds it in place.

**Raphael is the exception**, and the one fight here matched on *text*. The pack has no
`telos:glyph/bossbar/raphael.png` — none of its sixty-five bar glyphs is Raphael — so unlike the
others its bar appears to spell its name out, which the same identity string picks up. If the server
ever gives it a glyph the match goes quiet; `/aletheia boss` during the fight prints what to replace
the key with. Note that `seraphim` carries `raph` inside it, so only the whole of `raphael` is
allowed to match, and a test pins that too.

`/aletheia boss` lists every bar on screen with the texture it was matched on, its colour, and whether
it is a fight with phases, which is what to check if a pack update renames a glyph. Progress is read
unlerped where possible — following the bar's slide animation would call every phase late.

## Ambush and deathmark

Two calls that are **not the fight's** — they are the party's, so their percentages are settings
rather than a table:

| | |
| --- | --- |
| 65% | **AMBUSH** |
| 40% | **DEATHMARK** |

Everything above this reads what the server scripted; a phase lands whether you are ready or not, and
being told the wrong percentage for one is a real mistake. These two are yours: they fire once each
per fight as the bar drops past, both percentages and both wordings are settings, and emptying a
wording turns that one off and leaves the other. There is no early lead the way the phase warnings
have one — the percentage is the whole of the instruction, so if you want more notice, ask for it
higher up the bar.

**Only in the eleven dungeon bosses**, which is the point of the feature having a list at all:

| Dungeon | Called in |
| --- | --- |
| Celestial's Province | Asmodeus, Seraphim, True Seraph |
| Rustborn Kingdom | Valerion, Nebula, Ophanim, True Ophan |
| Neo Eden | Cherubim |
| The Voidlands | Sylvaris |
| Tenebris / Raphael's Chamber | Voided Omnipotent, Raphael |

A realm boss is dead before a reminder to hold something has finished being read, and a title over
every bar in the world teaches you to look past titles — which costs you the ones that matter. Apostle
and Hierophant are left out for the same reason from the other end: they are over at half a bar each,
so 65% of one is barely into the fight.

The list is `BossPhases.ENDGAME`, keyed on the pack's artwork like everything else here, so the same
whole-name matching applies — `onyx` is Raphael and never `onyx2` or `onyx_guardian`, `seraphim` is
the ordinary fight and never `hardmode_seraphim`. Note the pack spells Valerion **`valerion.png`**,
vowels in that order, whatever it looks like written down; get that wrong and the calls go quiet for a
whole dungeon with nothing else to show for it, so a test pins it.

`/aletheia boss` says whether the calls apply to what is on screen, which is the thing to check when
nothing fires: "not one of the endgame dungeon bosses" and "the match is broken" are otherwise the
same silence. `/aletheia test reminder` shows one wording per run, so both can be looked at without
finding a fight.

## Invulnerable phases

Every one of these fights has stretches where the boss takes no damage — a set piece, a shield, the
final bleed-out — and it says so by turning the bar over its head **blue**. That bar is small, it is
behind the boss, and it is the last thing you are looking at. So there is a readout of its own:
**INVULNERABLE** while it stays that colour, then **ATTACK** for a few seconds once it goes back,
since the window that opens then is the only time the fight actually moves. Every wording, every
colour and the hold time are settings, and emptying a wording turns that part off. There is an
optional sound on each change, off by default.

**There is a third state**, and it is the one that makes the loose test below worth its keep: a
**light purple** fill, where the boss takes *half* damage rather than none. It asks for the opposite
of what blue does — keep hitting, but do not spend the cooldowns you were holding for the real
window — so it gets its own line, **HALF DAMAGE**, and its own colour on the bar.

**It is not the boss bar**, which is where this was first looked for and is worth writing down. The
pack ships an ordinary, visibly different sprite for each of the seven `BossBarColor` values, so a
blue boss event would simply show as a blue bar at the top of the screen — and it never does. The
fights settle it outright: Hierophant goes invulnerable behind a **red** boss bar, and the Cog
Sentinel stays hittable behind a **blue** one.

A bar over a mob is **three text display entities standing in the same spot**: a frame, a single fill
glyph, and the mob's name spelled out. All three are drawn in `telos:text`, so every character is a
picture — `telos:text/xikage/miniboss/t.png` is the letter t, which means the name can be read back
out of the texture names. The fill is one glyph, `xikage/default/inner.png`, and **its text colour is
the whole state**:

| | |
| --- | --- |
| `#5AD022` | the mob can be hurt |
| light purple | it takes half damage |
| `#1757A7` | it cannot be hurt at all |

**The healthy colour is a gradient**, though, not the single value above — it shades from green
through yellow to red as the mob's health drops (`#5AD123` at full, `#5EC821` at 96%, `#62C120` at
92%), and nothing says the server does not shade the other two the same way. So the exact colours are
tried first and then a looser test, which sorts a fill **by hue in two steps**:

| | |
| --- | --- |
| blue clearly beats green | it is off the green-to-red health ramp at all |
| red also beats green | purple — half damage — rather than blue |

Nowhere on a green-to-red ramp is blue anywhere near the front, and purple is red and blue together
where the invulnerable blue has almost no red in it (`#1757A7` is 23 red against 87 green, the wrong
way round by a mile). Both steps are a hue rather than a shade, so they hold however light or dark
the server draws either one. The exact colours and the loose test are all settings; the purple's
exact value ships empty, since the hue test does not need it and `/aletheia boss` prints it during
the phase for anyone who wants it pinned.

Two things keep it off the wildlife, since nearly every mob on Telos carries a bar:

| | |
| --- | --- |
| A boss bar has to be on screen | the server puts one up for a boss and nothing else |
| The mob has to be boss grade | its frame says which: `xikage/{boss,miniboss,default}/` |

Both are settings, and the grade one is a real split rather than a guess: in the Cog Sentinel's arena
the Sentinel reads `boss` and every mob milling around it reads `ordinary`. There is also an optional
dimension filter, empty by default so the realm bosses out in the open world still count.

**Which bar inside the fight** is the best grade in range, nearest among equals, except that a bar
*named by one of the boss bars* outranks everything — Hierophant's floating bar spells "hierophant"
next to it, which is the server naming the fight rather than this guessing at it. That is only a
preference because it does not always help: the Cog Sentinel's bar carries its health where its name
would go. A better bar is looked for once a second; in between, only the followed one's colour is
re-read, so the state keeps up with the fight without scanning the arena for it.

**Read the fill every** is how often that re-read happens, in ticks — every other tick out of the
box. Reading the colour means walking the bar's text and looking its glyph up in the resource pack,
and a boss going untouchable is a thing that lasts seconds, so every other tick sees the change a
twentieth of a second later for half the work. Turn it up on a machine that needs the frames, or down
to 1 if you would rather it were exact. The search for a better bar is on its own clock and is not
affected either way — and a search that finds nothing now waits out that clock like any other, rather
than reading every display in the arena twenty times a second to be told the same thing.

`/aletheia boss` lists every bar in range with its colour, grade and name, and says which one is
being followed. `/aletheia test invulnerable` walks the readout — and the boss bar below — through
all three states, so they can be placed and their wordings checked without finding a boss.

`/aletheia mobscan <note>` is the tool that found all this and the one to reach for if a pack update
breaks it: it writes every boss bar and every named entity within 64 blocks to `logs/latest.log`,
broken into segments with the colour, font, text and glyph textures of each. Run it once in each
state with a different note, and whatever differs between the two runs is the thing to watch.

## A boss bar of your own

The server's bar is 182 pixels wide, at the top of the screen, in the pack's artwork, and says
nothing about any of the above. So there is one of this mod's own that can be **moved, resized,
shaped and coloured by the state** — and it takes the original off the screen so there are not two.

It is not a second reading of anything. The health is the same `LerpingBossEvent` the vanilla overlay
draws, animation and all; the state is the fill over the boss's head, read exactly as the readout
above reads it. What changes is everything about how it is presented:

| | |
| --- | --- |
| **What to draw** | the bar with its words, the words on their own, or the percentage alone |
| **Fill colour** | green, purple or blue, following the three states — or one fixed colour |
| **Words** | the boss's name, the percentage, and the state in words, in any combination |
| **Where the words go** | above the bar, on it, or below it |
| **Ends** | square, rounded, bevelled, slanted or notched, at any depth |
| **Size** | width, height, and a scale on top of both |
| **Placement** | dragged in `/aletheia hud`, or centred, which is where it starts |

**The bar itself is optional.** *Words only* drops the shape and keeps the line — `Cherubim  ·  48%`
— and *Percentage only* is the figure and nothing else, whatever the word switches say. The
percentage is the one thing the server's bar cannot tell you precisely, and this is it without the 182
pixels of artwork it normally arrives wrapped in. Everything else holds: the same reading, one line
per boss, dragged, scaled and coloured the same way. What changes is that a readout with no shape is
sized by its words like every other line the mod draws, so Width, Height, Ends, Outline and Backing
stop applying. With every word switched off as well there is nothing left to draw, and the editor
stands its own label in so you can still find it and switch it back on.

The shape is drawn a row of pixels at a time rather than blitted from a sprite, which is what lets
the ends be cut into any of those at any size — and the outline is worked out from the shape itself,
by asking which parts of each row its neighbours do not cover, so there is no artwork to redraw when
the numbers change. The fill boundary stays upright regardless: it is where the health is, and a
shaped end must not tilt it.

**The name is a picture** on most of these fights — `telos:glyph/bossbar/lotil.png` and no letters at
all — so it is recovered from the texture name and tidied up (`hardmode_ophanim` → "Hardmode
Ophanim"), unless it is a fight `BossPhases` already knows, which names it properly, or one that
spells its name out normally, which is used as sent.

The glyph itself has to be **dropped by asking the pack**, not by its codepoint. Telos hangs its
pictures wherever it likes — `U+160E4` on one of these bars, ordinary CJK ideographs on the chat
lines — so there is no range to filter on, and drawing one in the game's own font gets you a box with
a codepoint in it. `HudGlyphs` already knows which codepoint is a picture in which font, since that is
how the bar was identified in the first place, so that is what decides. Anything surviving it still
has to look like a word — letters, digits and the punctuation a name might carry — so a glyph the
pack definition does not cover leaves the name empty rather than putting a box on your screen.

**Two bosses, two bars, two lines.** Apostle and Hierophant are a single encounter with a boss and a
bar each, and the server sends them as two bars on separate lines — so every boss bar on screen is
followed rather than only the first, and they are redrawn one to a line as well: a full-width bar per
boss, stacked in the order the server stacked them, with the gap setting between the lines. A second
boss costs height rather than halving the width, so each bar says its own name and its own percentage
in full and stays as readable as a lone one. The stack grows away from the corner it is anchored to,
so a bar parked at the bottom of the screen does not shift when the second one appears.

Which bars were picked up goes into the log whenever the answer changes, with the ones that were left
alone beside it. After the fight, "the second bar was not drawn" and "the second bar never reached the
client" look identical on screen, and `/aletheia boss` can only answer while the fight is still up —
which is the one time nobody is going to be typing it.

**Both of them die at 50%**, which is worth correcting rather than drawing. A bar sent as-is spends
that fight claiming there is twice as much left as there is, and the half that reads as full is health
you never get to take — so those two bars are rescaled to what can actually be taken off, and the
killing blow lands at 0% like every other fight. `BossPhases.endsAt` is the table, one line per boss,
and the setting under Boss bar turns it off if the server ever re-tunes them.

**Only the fight's bar is hidden.** Telos sends its whole HUD as boss bars — the potion counter among
them — so hiding "the boss bar" has to mean hiding that one and leaving the rest alone. It is done by
filtering the list the overlay walks rather than by skipping a draw, so the bars below simply move up
instead of leaving a gap, and nothing the server sent is thrown away: turning the setting off puts
the bar back on the next frame.

`/aletheia boss` says whether the bar has a fight, names every bar it is drawing and where each one
stands — printing the server's own reading beside it where the two differ — and what it has taken off
the screen. `/aletheia test invulnerable` walks a sample bar through all three states, so it can be
placed and coloured without finding a boss.

## Cog Sentinel

The Sentinel's opening is not on the health bar at all: five peripheral stabilisers have to come down
first, and each one is announced in chat.

| In chat | On screen |
| --- | --- |
| `(1/5) Cog Stabilisers destroyed` | **1/5 COG** |
| `(4/5) Cog Stabilisers destroyed` | **4/5 COG** |
| `(5/5) Cog Stabilisers destroyed` | **COG ACTIVE** |

The last one is the whole point — the server follows it with `PERIPHERAL STABILISERS DESTROYED` and
the boss itself becomes worth hitting — so it gets its own wording and its own colour rather than
being another number. Empty **Last stabiliser title text** to make 5/5 count like the rest.

Note the counter is at the **front** of the line here, not the end, which is the one thing separating
it from the Neo-Eden score title. It is matched first regardless, so a re-word that moved the counter
to the end could not quietly turn the fight into dungeon score; a test pins that. The total is read
off the line rather than assumed, so a re-tune to a different number of stabilisers costs nothing.

`/aletheia test cog` walks 1/5 up to the last one, one per run.

## Quietening the passenger warning

Telos makes the vanilla client log

```
[Render thread/WARN]: Received passengers for unknown entity
```

two or three times a second, from the moment you join — **938 of them in one six-minute session**,
peaking at 13 in a single second. The server builds its nametags and displays by stacking entities,
and the riders routinely arrive before (or after) the thing they ride, so the client is told about a
vehicle it has never seen. Everything else in the log, including this mod's own `debugLogging`
output, drowns in it.

`ClientPacketListenerPassengerLogMixin` redirects the single `LOGGER.warn` call inside
`handleSetEntityPassengersPacket` and nothing else. Vanilla's reaction to the packet is to ignore it
and return, which it still does — only the logging changes. The lines are counted rather than
dropped, one summary is printed a minute, and `/aletheia status` shows the running total.

**This is not a frame rate fix**, and it is not sold as one — two or three log lines a second cost
essentially nothing. It is the log's readability that this is for.

The injector is `require = 0` on purpose, unlike every other mixin here: it is cosmetic, and a
renamed vanilla method should bring the warning back rather than refuse to start the game. That is
why `/aletheia status` prints the count even when it is zero — a zero that stays zero while the log
still spams is the injection no longer applying.

## HP pots

A HUD line reading `Pots: 1/5`, in the same draggable, scalable readout as Nature's Gift below.

The count is not counted here — it is already on screen. It is one of the fields of the server's
MythicHUD boss bar, drawn in the pack font
`mythichud:layout/rotmc2-layout/fonts/rotmc2-hud/hppot`, so the readout finds that run by its font.
Matching is on a substring of the font name (**Read the part whose font contains**, `hppot`), so a
pack renaming its layout folder does not break it.

**That field is not text.** It is a single private-use codepoint that draws a picture, one glyph per
amount — five pots left is `U+1606A`, whose texture is `hud_hp5.png`; four is `U+16069` and
`hud_hp4.png`, up to `hud_hp10`. Reading the characters gives nothing at all, so the number comes
from the *texture* the pack maps that codepoint to, which `HudGlyphs` reads out of the font
definition — the same lookup that names the pictures in `/aletheia hudscan`. Since the server only
publishes how many are left, **Out of** supplies the total.

Re-read on a timer and cached, not per frame — the scan walks every channel on screen: every boss
bar, every row of the player list, the whole sidebar. **Read the count every** sets the gap, five
times a second out of the box, which is already far more often than a number that changes when you
drink can matter. The line turns amber at a third left and red at none. `/aletheia pots` prints the font it matched and the
texture it read the number off, which is what to check if a pack update renames either.

## Shortening chat

Toggling other players on and off is announced with a full sentence, which reads as news rather than
as the state it is:

| In chat | Becomes |
| --- | --- |
| `You can once again see other players.` | `Players: [shown]` in green |
| `You have hidden other players.` | `Players: [hidden]` in red |

Both replacements are text fields — **empty removes the line from chat entirely**, which takes two
events rather than one: `MODIFY_GAME` swaps the text, and `ALLOW_GAME` drops the line, since a
modified message cannot express "nothing" (an empty component is still a blank line).

## Hiding the action bar

**Action bar → Hide the server's action bar.**

Hides text above your hotbar. Leaving the filter empty hides *everything* there, including vanilla
held-item names; run `/aletheia actionbar` to see what the server has recently sent, then copy a
distinctive word into **Only hide lines containing**.

> This is done with a mixin on `Gui.setOverlayMessage` rather than Fabric's message events.
> `ClientPacketListener.setActionBarText` calls the Gui directly without going through
> `ChatListener` — which is what Fabric's events hook. `Gui.setOverlayMessage` is where both routes
> finally meet, so it is the only place that catches everything.

**This does not hide the Telos readouts** — the stat panel, the biome ribbon, the shard counter.
None of them are on the action bar. See below.

## Where the server's readouts actually come from

They are not drawn by a mod, and not by the channel they appear next to. Telos runs MythicHUD: the
text is an ordinary chat component drawn in a font from the server's resource pack, whose glyphs
carry a huge negative `ascent` (about `-105500`). The pack also replaces the core
`rendertype_text` shader, and that shader turns the ascent back into an id and moves each glyph to a
fixed corner of the screen:

```glsl
float id = get_id((round(MH_OFFSET - pos.y)) * -1);   // ascent -> HUD slot id
...
pos -= vec3(xOffset, yOffset, 0.0);                    // slot 149 = bottom right
```

So where a line *appears* says nothing about which packet carried it, and hooking the action bar
catches none of it. What the text does carry is its font — a `mythichud:…` id rather than
`minecraft:default` — and that is a dependable marker.

`/aletheia hudscan` lists every piece of text the server currently has on screen — action bar, title,
subtitle, boss bars, tab list header/footer, scoreboard sidebar — and flags the ones drawn in a
resource-pack font, naming the channel each came through. Run it while the readout is visible.

On Telos it prints a single boss bar holding the lot:

```
 Boss bar 1: Radiant Isles 30.2 0
   mythichud:layout/rotmc2-layout/fonts/rotmc2-hud-biome/biome-text = "Radiant Isles"
   mythichud:layout/rotmc2-layout/fonts/rotmc2-hud-health/health-text = "30.2"
   mythichud:layout/rotmc2-layout/fonts/rotmc2-hud-cooldown/cooldown-text = "0"
```

Glyphs that draw a picture rather than a letter are named too, by reading the font definition out of
the loaded pack — so `U+E000` is reported as `mythichud:assets/rotmc2/plate.png` instead of leaving
you to guess.

### Hiding parts of it

**Server HUD → Hide parts of the server's HUD**, then put a word into **Parts to hide**. It matches
either a font name or a picture's texture name, whichever is more convenient: `biome` for the biome
caption, `health` for the health number, `plate` or `ribbon` for a background the scan names. An
empty filter hides the whole HUD.

Matching is per glyph, not per font, because a caption and the ribbon behind it are separate glyphs
and the ribbon usually shares a font with panels you are keeping.

Hiding cannot just delete the text. The parts are laid out by a chain of negative-space glyphs and
the whole string is centred, so dropping a run would drag everything after it sideways. Each hidden
run is swapped for an **equal width of blank space** instead: the pack's `mythichud:spaces` font has
one codepoint per pixel of advance (`U+E000 + n` right, `U+F000 + n` left, up to 1280), so the swap
is exact and nothing else moves. It is done at render time, so switching the setting off brings the
parts straight back.

## Hiding props standing in the world

**World → Hide props standing in the world.**

Some things a fight puts in front of you cannot be dealt with in game. The orb an Arcanist plants
stands between you and the boss for the whole of its life; the void scenery Maelstrom throws over a
mob covers the mob. Neither is a block or a mob — they are usually **display entities**, whose only
job is to hold an item or a block in the air, so there is nothing to target, nothing to walk through
and nothing to break, only something to see past. This declines to draw them.

Run **`/aletheia props`** while the thing is in front of you. It lists what is standing within 16
blocks (`/aletheia props 32` to widen it), folded by kind with a count, printing exactly the strings
a filter is matched against:

```
Things within 6 blocks of zombie "targetdummy" (nearest first)
 • 0.0m  item_display  model=arcanist_orb_n1a/  item=bone  x18  (10 parts)
 • 0.3m  item_display  model=internal_fire/  item=bone  x35  (2 parts)
 • 0.4m  area_effect_cloud  x14
 • 0.9m  item_display  model=chromafire_tornado/  item=bone  x27  (2 parts)
```

It centres on the **nearest mob**, not on you, because that is where props are — they are spawned on
top of the thing they belong to. `/aletheia props <blocks>` sets the radius, and `/aletheia props me`
measures from you instead.

Copy a distinctive word into **Types or models to hide** — a comma-separated list, matched anywhere
in any of those strings. Trimming a phrase covers more: `arcanist_orb` takes every tier of the orb
rather than the one.

**Go by the count, not by the name.** A model id says what the pack author called the thing, not what
it looks like in the fight: the effect that reads on screen as a cloud of void is filed under
`chromafire_tornado`. Scanning the list for the word you would use for it is the reliable way to miss
it. The row with a few hundred entities on it is the thing covering your screen.

**One prop is many entities**, which is the thing that makes a plain listing useless. Telos builds a
prop out of one display per part and animates it with one display per frame: the monolith alone
arrives as `arcanist_orb_n1a/orb`, `/body`, `/tophalf`, `/rune` and seven separate tentacle segments,
and a fire effect is `fire_0`, `fire_1`, `fire_2`… Listed as they come, one orb is ten rows and
everything else falls off the end of the report.

So a row is folded to the model's **folder**, which is one row per prop and is also the word worth
filtering on; where there is no folder, the frame number comes off instead. The `(10 parts)` on the
end says how many models the row stands for. Folding only ever *shortens* a path, so what is printed
is still a substring of every model behind it and can be pasted straight into the filter.

`/aletheia mobscan` will not find these. That one lists things *wearing a name*, because it is
looking for a boss's health bar, and a prop generally has no name at all.

**Names are not matched, on purpose.** Telos writes them in its resource pack's own font, so a name
is a run of picture glyphs rather than the word it looks like — the same reason Afterburner goes by
model id. The scan folds them down to letters so you can still recognise what you are looking at, but
the entity type and the model, item or block a display is holding are the things to filter on.

**An empty filter hides nothing** — the opposite of the action bar and server HUD filters above.
Everything there means every line of text; everything here would mean every entity in the world,
which is not a state to arrive in by clearing a text box. For the same reason you are never hidden
from yourself: `player` is an ordinary word to end up with in a filter, and vanishing in third person
is a puzzling way to find that out.

> This is a mixin on `EntityRenderDispatcher.shouldRender`, the one gate every entity passes through
> before it is drawn. Nothing is removed and nothing is moved — the entity carries on existing for
> everything that is not the renderer — so turning the setting off puts it back on the next frame.
>
> There is no opacity setting because entity rendering has no opacity to set. Alpha is a property of
> the render type each model layer picks, not of the entity, so "half transparent" would mean
> rebuilding those layers per renderer and would still sort wrong against everything behind it.

## Magnificat's domain

**World → Magnificat domain.**

Magnificat drops a ring of fire on the floor, and the only thing that matters about it is which side
of it you are standing on. What the server draws answers that badly. The circle is a picture on a
flat square hung a hair above the ground, so at a normal camera angle it is a thin ellipse of glare,
and as the camera comes level with the floor it disappears altogether — which is exactly when you
need it, because that is the angle you fight at. On top of that there is the fire itself, the
particles, and half a raid standing on the line.

So the circle is left undrawn and a plain ring is put in the same place, **coloured by which side of
it you are on**: green while you are inside, red while you are out. That is the reading you wanted
from the fire, taken once a frame off your actual position rather than judged by eye through the
glare.

```
Magnificat domain
 Circles to replace: "magic_fire_circle2/out"
 Ring: drawn
 Server's circle: hidden
 Radius setting: 0 -- measured from the circle
Found 1 circle (nearest first)
 • 4.2m out  inside  r=9.0  (measured 9.0)  modelengine:magic_fire_circle2/out
```

**The radius is measured, not assumed.** The circle is an item display, and the model it holds is a
three-block square with the ring painted corner to corner — so the radius on the ground is a block
and a half times whatever scale the display was spawned at, which comes to nine blocks for
Magnificat. Reading it off the entity means the ring is the size the fire actually is, that it grows
with the circle while it interpolates in, and that it stays right if the ability ever turns up in
another size. **Ring radius** is the override for the day that measurement stops being true;
`/aletheia domain` prints both numbers so you can see whether it still is.

**Two switches, not one.** Drawing the ring and hiding the server's circle are separate settings, and
the interesting states are the ones where they disagree: the ring drawn *over* the real circle is how
you check the two line up, and hiding on its own is for anyone who just wants the glare gone.

**Ring height** stands it up as a low wall rather than laying it flat. A flat ring is the tidier
picture and vanishes edge-on; two tenths of a block is enough to keep it visible from any angle
without becoming a fence you cannot see the fight through. **Ring solidity** is the other half of
that trade.

The filter is matched against the **item model** the display is holding, the same as the prop filter
and for the same reason: it is plain ASCII the server picked, where a name would arrive as a run of
picture glyphs. An empty filter switches the whole thing off however the two ticks are left.

> The ring is drawn from Fabric's `BEFORE_GIZMOS` level render event, into the `debugQuads` render
> type — untextured, translucent, position and colour only, depth tested but not depth writing. So a
> wall in front of it hides it, which is what makes it read as lying on the floor, and it never
> occludes anything drawn after it.
>
> Not the line render type: a line is a fixed width in *pixels*, so the far side of a nine-block ring
> would draw as thick as the near side and the whole thing would read as flat. Quads thin with
> distance, which is what makes it a boundary you can judge by eye.
>
> Circles are found on the client tick and drawn on the frame. Walking every entity in the world is
> not work to do at frame rate when the answer only changes twenty times a second; asking the handful
> already found where they are is, or the ring would swim while the camera moved.
>
> Hiding is the same `EntityRenderDispatcher.shouldRender` gate the prop filter uses, asked as a
> second question rather than folded into that filter — this circle is hidden because something is
> drawn in its place, so it belongs to the ring's own setting.

## Nature's Gift

A HUD line reading `ngift: ready` or `ngift: 5:23`, shown while ability boots are worn. Green when
ready, amber under 15 seconds, red otherwise. **Label** and **Ready text** set both halves of what it
says when it is off cooldown; an empty ready text leaves the label on its own.

An ability piece is recognised by the **`Cooldown` line in its lore**, not by its name. Item names
are drawn in the server's custom font -- private-use glyphs, small capitals, typographic
apostrophes -- so matching them is fragile. The cooldown stat is plain text and is what makes a
piece worth tracking anyway. Set **Only track boots named** if you want to restrict it to one
particular item; leave it empty (the default) otherwise.

Telos starts armour abilities through the **vanilla item cooldown system** — the same mechanism as
the sweeping overlay on a thrown ender pearl — so `ItemCooldowns.getCooldownPercent` is how the proc
is spotted. The item's lore supplies the *total* duration, from its `Cooldown » 360s` stat line; the
lore never counts down, so it cannot be read as the remaining time. If an item states no cooldown,
the configured fallback length is used instead.

**The countdown itself is the mod's own.** Following the server's number live does not work: taking
the boots off clears it, putting them back on does not bring it back, and the readout snapped to
`ready` on every swap while the ability was in fact still down. So the server's reading is only ever
allowed to **add** time, never to take it away — the moment it reports more left than the timer has,
the timer is set to match, and otherwise it just runs on its own clock. That still covers the proc, a
relog part-way through (the server hands back however much is genuinely left), and anything that
extends the cooldown.

Because the count is ours, the resets the server does are followed deliberately:

| | |
| --- | --- |
| Entering, leaving or moving through a dungeon | cleared |
| Being sent to another world | cleared |
| Changing server — hub to realm, realm to realm | cleared |
| Disconnecting | cleared |
| Swapping to *different* ability boots | cleared, and the new pair is followed instead |

All of those reach the client as a change of dimension (`telos:realm` → `telos:neo_eden/1`), except
the server change, which arrives as a fresh login. There is no checking back against the server
afterwards, either: `ClientPacketListener.handleRespawn` builds a new `LocalPlayer` and the cooldown
map lives on the player, so vanilla's own reading is empty on the far side of any of these whatever
the server still thinks.

The boots the timer belongs to are identified by their **cooldown group** — the id vanilla files the
cooldown under, read off the item. Two pieces sharing a group share a cooldown, exactly as the server
sees it, so putting the same boots back on continues the count rather than starting a new one.

**The proc gets a title.** The ability going off is easy to miss in a fight, and it is the one moment
that matters — everything after it is just the countdown. Title text, subtitle, colour and sound are
all settings, and `{time}` in either line is the ability's full cooldown in whichever format the
readout is set to. `/aletheia test ngift` shows one without waiting for a proc.

What counts as a proc is a count starting from **near enough the full cooldown** — 97% of it, ten
seconds of slack on a 360 second ability. That test is what separates the ability firing from a
cooldown that was already running and has only just come back into view: a relog part-way through
hands back whatever is left, and announcing that would be a lie about when it fired.

`/aletheia ngift` prints the item name, the timer next to what the server currently claims, the total
parsed from lore, the dimension being watched, why the timer was last cleared, and every lore line —
enough to tell which half of the calculation is wrong if the readout ever looks off.
`/aletheia ngift reset` clears a timer by hand, for a proc missed while the boots were off.

## Dungeon timer

Telos prints the clear time, and your personal best, in its own end-of-run leaderboard:

```
☠ Abyss of Demons ☠
☠ Defeated Malfas in 24s
```

So there is deliberately **no completion title, no split reporting and no chat rewriting here** —
all of that would be the mod saying, worse and a second later, what the leaderboard already said.
What the server does not give you is any of it *while you are still running*. That is the whole
feature: a clock, and the time to beat next to it.

```
run 1:23  PB 1:15
```

The clock turns red the moment it passes your best. On its own that is the only comparison it can
honestly make — it knows how long you have been in here and nothing about where in the run that is.
The split list below is the other half of that: it does know.

**The best is read off the server's line wherever the server states one.** A stopwatch started on a
dimension change cannot agree with Telos to the second, and a personal best that disagrees with the
leaderboard is worse than no personal best at all. Fractions are dropped rather than rounded for the
same reason — `24.6s` is filed as `24s`.

The exception is a dungeon with stages, below: Telos times each stage separately and never states a
figure for the whole run, so there the run's total is this clock — the only number for it that exists.
Each *stage* still keeps the server's own.

Bests live in `config/aletheia-bests.json`, not in the settings file. Nothing there is something you
*chose*, and a "reset to default" button that wiped your times would be a cruel thing to put next to
a colour picker.

### A dungeon is not one fight

```
Celestial's Province
Asmodeus                3:41
Seraphim         7:18  -0:12
True Seraph               --
```

The clock used to stop dead on the first `Defeated` line. In the three dungeons people actually run,
that was somewhere near the beginning:

| | |
| --- | --- |
| **Celestial's Province** | Asmodeus, then **Seraphim in the same room** — no portal in between — then True Seraph through the one that drops when Seraphim falls |
| **Rustborn Kingdom** | Valerion, Mithrion & Nebula together, Ophanim, then True Ophan in the Dawn of Creation |
| **Neo Eden** | Apostle & Hierophant, whose two clear lines arrive in the same second, then Cherubim |

So a clear is now a **split**, and only the last stage of a dungeon's list ends the run. The list is
in `DungeonRoutes`, keyed on the area name the ribbon shows and on the boss names the server's own
clear lines carry — "True Seraph" and "True Ophan", not the fuller names people use for them.

**A stage is not always one boss.** Mithrion is fought alongside Nebula and the server has never
announced its defeat — 1027 mentions in the logs and not one clear line — so the row reads for both
and answers to either. Neo Eden's first row is the same shape from the other direction: two lines
arrive for it, the first fills the row and the second finds it already done.

**Raphael's Castle is deliberately absent.** The number of Dark Champions before Raphael is not
fixed — runs in the logs show two, one and none — and a list that guesses would either stop the clock
early or never let the run finish at all. A dungeon with no list still gets a clock and still gets a
split line per boss it announces; it just cannot show you what is still to come.

**Each stage keeps its own best**, from the server's clear line for that boss, so a row compares like
with like and agrees with the leaderboard you have just read. That matters because *what the server
means by a stage's time is not the same everywhere*: in Celestial's Province Asmodeus and Seraphim
share a room and a clock, so Seraphim's 7:18 includes Asmodeus' 3:41, while Rustborn times each stage
from its own start and its four numbers add up to the run. Nothing here reconciles the two. Deriving
the splits from this mod's clock instead would give a tidier column and a worse readout — it would
disagree with the leaderboard by minutes in Rustborn, and the leaderboard is what you are looking at.

Names go against the left edge and times against the right, measured out of the font rather than
padded with spaces, since a split list whose numbers do not form a column is most of the point thrown
away. Stages you have not reached are greyed; one that beat its own best turns green; one that came
in behind shows how far.

### Knowing you are in a dungeon

**The dimension id says whether you are in a dungeon. It cannot say which one.** Telos hands a
dungeon out as an instance slot, so Celestial's Province is `telos:dungeon/2` on one run and
`telos:dungeon/11` on the next, and every other dungeon draws from the same fourteen ids. What is
constant is the *shape*: the open world is `telos:realm` and a dungeon room is a numbered
`<name>/<number>`. That is the whole test, and it needs no list — a dungeon Telos adds tomorrow is
timed the day it appears.

This is what was wrong before. The mod matched the id against a list of dungeon names shipping with
**`dreadwood`** in it, and there is no Dreadwood dimension — the Thicket is `telos:dungeon/<n>` like
everything else. The clock had never started anywhere. **Dungeons → Extra dimensions that count as a
dungeon** survives as an escape hatch for a dungeon shaped differently, and is empty by default.

**The run is named from the ribbon in the corner of the screen** — the same "Permafrost" /
"Celestial's Province" readout the server already draws, read off the boss bar that carries the HUD
(see [where the server's readouts come from](#where-the-servers-readouts-actually-come-from)). That
is what the personal best is filed under, so the times are keyed by a name you would recognise rather
than by a slot number that means nothing twice.

**The name settles on the first boss, and is frozen from then on.** Two things pull in opposite
directions here.

*The ribbon lags the dimension change.* You arrive in `telos:dungeon/1` a beat before the server's HUD
catches up, so the first reading taken inside is the biome you walked in from. Latching it — which is
what this did at first — named a Celestial's Province run **Shadowlands**, and the label was the
smaller half of the damage: no boss list is filed under Shadowlands, so Asmodeus' clear was taken for
the end of the run and the clock stopped two minutes in.

*But a dungeon is more than one area.* Celestial's Province gives way to Seraph's Domain for the boss,
Rustborn Kingdom to The Dawn of Creation, and each is a fresh dimension too. Following the ribbon that
far would rename the run at the boss door and file the boss room alone as a personal best.

So the ribbon is re-read until the **first split lands**, and frozen from then on. Waiting a fixed
second instead would be guessing at the server's lag — too short on a bad connection, a pause for
nothing on a good one. What can be said exactly is when the name stops being a guess: when the run has
produced something filed under it. Every area change that matters comes long after that.

`/aletheia timer` prints the dimension you are standing in and whether it counts, what the server
calls the area, and every best on file. `/aletheia timer reset` drops a stuck clock;
`/aletheia timer clear` wipes the bests.

A run's numbers stay on screen for half a minute after you walk out — the clock frozen where it
stopped, the splits as they fell — so leaving does not take them with it before they have been read.
That is measured from **leaving**, not from the last kill, which it used to be: a run you stood
around in for a minute after the boss died lost its readouts the instant you crossed the threshold.

## Moving the readouts

`/aletheia hud` opens the **readout editor** over the game, as does the **Open editor** button beside
every readout's settings and the key of that name under *Options → Controls → Aletheia*:

| | |
| --- | --- |
| Drag | move a readout |
| Scroll | resize it, 25%–400% |
| Right-click | switch a readout on or off |
| Arrows | nudge a pixel at a time, ten with Ctrl |
| R | back to defaults |
| Shift | drag without snapping to edges and centre lines *(not listed on screen)* |
| Esc | save and close |

Nothing is drawn twice while it is open — the editor renders each readout through the same code the
HUD does, so what you line up is what you get back in play. A readout that is currently hidden still
appears there, so it can be placed before it ever fires.

**Switched-off readouts appear too, faint, captioned `OFF`.** They are the ones most easily lost:
a tick box buried three tabs into the settings is a poor way to find out that the mod can show you
something. Here they are all in front of you at once, and the right mouse button is the switch — so
the screen that places the readouts is also the one that lists them.

Dragging writes the same settings the **Placement** sliders edit: the corner it was dropped nearest,
and the distance in from that corner. So the two ways of moving a readout stay in step, and one
parked against an edge keeps its distance from that edge when the window is resized. Offsets are in
scaled GUI pixels, so they survive a change of *GUI Scale* too.

## Building

Minecraft 26.1 ships unobfuscated, so this uses Mojang mappings and the non-remapping
`net.fabricmc.fabric-loom` plugin. There is no Yarn and no `modImplementation`.

**Gradle must run on Java 25** — Loom 1.17 requires it.

```sh
JAVA_HOME=/path/to/jdk-25 ./gradlew build
```

The jar lands in `build/libs/`, and is also copied straight into the Minecraft instance pointed at by
`mods_dir` in `gradle.properties` — so a build is immediately playable, no dragging files around.
Any previous build of *this* mod is removed first, since two jars with the same mod id stop the game
booting. Other mods in that folder are never touched.

```sh
./gradlew build                       # build + install to mods_dir
./gradlew build -Pmods_dir=           # build only, skip the install
./gradlew installToInstance           # install without a full rebuild
```

`mods_dir` is a machine-specific path. If you ever share this repo, move that line to
`~/.gradle/gradle.properties` instead.

In IntelliJ: *Settings → Build Tools → Gradle → Gradle JVM* must be set to a JDK 25.

```sh
./gradlew test        # exercises the chat matching rules
./gradlew runClient   # dev client
```

## Layout

```
dev/landofif/aletheia/
├── AletheiaClient      entrypoint
├── Aletheia            mod id, name, logger
├── ChatWatcher         wires Fabric's chat events to the parser and the titles
├── AletheiaCommands    /aletheia
├── boss/
│   ├── BossPhases      where each fight's phases begin -- pure data, unit tested
│   ├── BossPhaseTracker  reads the bar and calls the change before it lands
│   ├── BossPhaseHud    the Cherubim 63% -> BLACK HOLE readout
│   ├── BossReminders   the party's own AMBUSH / DEATHMARK calls, in the dungeon bosses only
│   ├── BossBars        the bars on screen, and which of them is a fight
│   ├── MobBars         the bars over the mobs, and the colour of each fill
│   ├── FillState       which of the three states a colour means -- pure, unit tested
│   ├── Vulnerability   follows one bar and reports what that state is doing
│   ├── VulnerabilityHud  the INVULNERABLE / HALF DAMAGE / ATTACK readout
│   ├── BossBar         which fight the mod's own bar is drawing, and what it hides
│   └── BossBarHud      that bar: its shape, its fill and its labels
├── chat/ChatRewrite    shortens the server's wordier lines, or drops them
├── detect/
│   ├── ChatText        small-caps + colour-code folding, so the regexes see plain ASCII
│   ├── NeoEdenParser   the matching rules -- no Minecraft types, unit tested directly
│   ├── CooldownText    reads the "Cooldown » 360s" stat off an item, also unit tested
│   ├── StatText        reads a stat panel's rows, icons or plain words -- unit tested
│   ├── SpacingText     the pack's one-pixel-per-codepoint spacing font
│   └── DungeonEvent    what was matched
├── dreadwood/
│   └── DreadwoodRun    how many rooms of a Thicket run are done, and when that count ends
├── timer/
│   ├── DungeonTimer    the run clock: when it starts, when it stops, what the run is called
│   ├── DungeonDimension  whether an id is a dungeon instance -- pure, unit tested
│   ├── DungeonRoutes   the bosses each dungeon puts up, in order -- pure, unit tested
│   ├── DungeonSplits   which of them are down this run, and what each one took
│   ├── DungeonTimerHud the run 1:23  PB 1:15 readout
│   ├── DungeonSplitsHud  the two-column split list
│   └── DungeonBests    the times to beat, in config/aletheia-bests.json
├── gift/
│   ├── NaturesGift     boots-slot detection, and the mod's own cooldown timer
│   └── NaturesGiftHud  what the line says, and in what colour
├── afterburner/
│   ├── Afterburner     the held ability, its cast, and the second shot seven seconds later
│   └── AfterburnerHud  the countdown to that shot
├── stats/
│   ├── PlayerStats     your stats, read out of the tab list's fake player rows
│   └── PlayerStatsHud  them, in a corner, without holding Tab
├── pots/
│   ├── HpPots          reads the server's own potion count out of its HUD font
│   └── HpPotsHud       the Pots: 1/5 readout
├── hud/
│   ├── AletheiaHud     placement, scaling and drawing shared by every readout
│   ├── AletheiaHuds    the list of them, hooked into Fabric's HUD
│   └── HudEditorScreen /aletheia hud -- drag and scroll to place them
├── actionbar/ActionBar what to do with text headed above the hotbar
├── serverhud/
│   ├── ServerHud       finds the server's own readouts by their resource-pack font
│   ├── HudArea         the area name off the server's ribbon -- "Celestial's Province"
│   ├── HudGlyphs       names the picture behind a glyph, from the pack's font definition
│   ├── MobScan         /aletheia mobscan -- dumps the bars over the mobs to the log
│   └── ServerHudFilter blanks out chosen parts without moving the rest
├── world/
│   ├── Props           which things in the world the client declines to draw at all
│   ├── PropScan        /aletheia props -- names what is standing in front of you
│   ├── Domains         finds Magnificat's fire circle and puts a legible ring in its place
│   └── DomainRing      that ring, as quads on the ground
├── mixin/              the action bar choke point, the flat-title redirect, the boss bar
│                       filter, the entity-render gate, plus accessors for /aletheia hudscan
├── ui/
│   ├── Alerts          vanilla title + sound output
│   └── Colours         colour settings, typed by hand and so typed wrong sometimes
└── config/
    ├── AletheiaConfig  the settings themselves, as plain static fields
    ├── ConfigFile      reads and writes them as config/aletheia.json
    └── ConfigScreen    the YACL screen bound to those fields
```

If the dungeon changes its wording and something stops being detected, turn on **Log matches to the
console** and check `logs/latest.log` — then the patterns in `NeoEdenParser` are the only thing that
needs updating.
