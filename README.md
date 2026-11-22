# Ski-E-O Together

Multiplayer for **Ski-E-O!**: host your resort or join a friend's and build it together, live.

## Download

Get the newest **`SkiEOTogether-…-manual-install.zip`** from the [Releases page](../../releases/latest),
then follow *Install* below. Everyone playing together needs the same Together version and the same Ski-E-O build.


Play in a friend's resort and build it together. One player hosts; up to 7 friends join, get a copy of the
resort automatically, and from then on what anyone builds shows up for everyone.

## How to play

Everything is in the game: click **Together** on the main menu (next to Mods) or in the pause menu (Esc).

**Host**
- *Start a NEW resort and host it*: the game's own New Game screen opens; once your new resort is up it is
  saved and hosted automatically.
- *Host one of your saves*: pick any save from the list (autosaves behind *Show autosaves too*).
- In the pause menu: *Host THIS resort* saves the resort you're in and starts hosting.

**Join**
- Click the friend who is hosting in the **Steam friends** list (people playing Ski-E-O have a dot).
  No address, VPN or port needed: it goes through Steam's relay network.
- Or type an address (LAN, Radmin/Tailscale/ZeroTier VPN address, or a forwarded TCP port 7777) and click Join.
- The host's resort downloads and opens by itself as `Together_<resort>`, a separate copy.

**Session** tab: players, **Refresh resort for everyone** (host) / **Ask the host to refresh** (friend),
**Leave session**. Name and an optional password are at the top of the menu (everyone types the same password).

**Lost connection**: if a friend's connection drops (Wi-Fi blip, VPN hiccup), their game keeps the resort open and
reconnects by itself for up to 90 seconds. They can keep playing meanwhile; what they build is shared once they are
back. If the host's resort is still the same one, nothing is downloaded again: the friend's game just catches up on what
changed (usually well under a second). If the host refreshed or opened another resort meanwhile, the friend's game
downloads it again, as when joining. After 90 seconds without the host, the friend's game returns to the main menu.

Pop-ups at the top of the screen tell you when someone joins or leaves, and when a refresh is needed.

**Pings**: quickly click the **middle mouse button** on any spot to drop a big arrow there with your name, so
everyone can see what you are talking about. Each player has their own colour (the host is gold; then blue, red,
green, purple, orange, cyan, pink). Everyone has one ping: middle-click somewhere else and your arrow moves there.
Middle-click near a ping to remove it (it doesn't have to be exact). Holding the middle button still turns the camera
as usual. A player's ping goes away when they leave. A short sound plays when a friend pings, and a ping that is off
your screen shows as a badge at the edge with an arrow pointing to it. Friends' name tags and the dots in the player
list use the same colours.

## What is shared

| Shared live | |
|---|---|
| Buildings, decorations, park features, snow guns | placed, moved, removed, opened/closed by anyone; shows up for the others in about a tenth of a second |
| Trails | drawn, extended, removed, renamed, difficulty/grooming/night-skiing, by anyone; shared right away |
| Lifts | built, removed, speed and hours, by anyone (can be opened and bulldozed everywhere); a friend's lift links to its trails and starts on everyone's game |
| Walkways (paths) | painted or erased by anyone; appears on the other screens while you are still painting (about 4 times a second) |
| Cleared brush / ground cover | cleared by anyone; appears on the other screens while you are still clearing |
| Felled trees | cleared by anyone (clearing tool, bulldozing, trail and lift corridors); shared within a second, never charged twice |
| Terrain shaping | raise, lower, smooth, flatten by anyone; shared about once a second while you shape; the other games redraw it once you pause for 2 s (no reload) |
| Base area and villages | follow from the shared buildings, lifts and walkways, so they come out the same on every game |
| Guests | each game runs its own guests through the normal arrivals. Since the November 21 Ski-E-O update (CL8231) mods can no longer remove guests, so a friend's crowd can end up bigger or smaller than the host's |
| Money | the host's balance is the resort's balance; friends' builds are charged to it |
| Clock | pause, speed and calendar follow the host; a friend changing speed does it for everyone. The game's month and year reports pause only the player reading them (the host's pauses everyone); a friend catches up after Continue |
| Resort management | open/closed status, discipline policy, ticket price, marketing campaigns |
| Staff | hiring, firing, assigning and sending staff home (lifties too), by anyone, shared right away. Each game's list of job candidates changes over time: when the person someone hired isn't on your list, your game hires a candidate with the same role in their place (names can differ, jobs and workplaces match). Lifties stay at their lift when a copy of it is rebuilt |
| Players | everyone sees everyone else as a skier/walker with a name tag |

Nothing needs a refresh any more in normal play: no reload, no camera jump, no freeze.

Walkways, brush and terrain: only the cells that changed are sent, so several players can paint, clear or shape in
different places at the same time. If two players change the very same spot at the same moment, the last one wins.

Guests go to the same lifts, trails and buildings, but they are not the same individual
people on every screen (copying hundreds of guests live would lag every game).

### Self-repair
Every 10 seconds each friend's game compares everything with the host's: every building and lift with its settings,
trails, staff, money, clock, walkways, brush and terrain. If something still differs 10 seconds later, that game asks
the host for just that part and fixes itself, without a reload. Buildings are fixed one by one, and anything someone is
still placing (or the host hasn't confirmed yet) is left alone, so a repair never undoes your recent work. The session
log notes it as DESYNC and repair.

### Resort refresh
Still there as a safety net: the host's game saves (over the resort being hosted, like pressing Save), everyone reloads
it (a few seconds) and lands back where their camera was, at the same speed. It never happens by itself. Use it only
if something looks different on two screens.

## Session logs (for reporting lag or errors)
Every time you host or join, Together writes a log to `mods\SkiEOTogether\logs\`, named like
`Together_host_2026-11-18_17-20-35.log` or `Together_friend_...`. It records your game build and Together version,
everything that was shared, any errors, every time the game froze for more than half a second (and what the mod was
doing at that moment), any mod step that took longer than 40 ms, a status line every minute (players, buildings,
lifts, guests, speed, money), a line per lift (status, liftie, riders, queue posts), the network messages of the last
minute, a report on every building or lift copied from the other player, and on the friend's side a CHECK every
10 s comparing the whole game with the host's (a DESYNC line names anything that differs, and a repair line what was fixed).

If something lags or breaks: open the Together menu, **Session** tab, **Report a bug**. It opens the bug report page in
your browser and the logs folder; attach the newest file there (both the host's and the friend's if you can). Nothing
is uploaded from the game itself. A pause right after joining, while the host's resort loads, is normal and is
labelled as such in the log. If the Together helper ever hits an unexpected error, it keeps running and writes it to
`mods\SkiEOTogether\bridge\helper-errors.log`; attach that file too if it exists.

## Same game version
You and your friends must be on the **same Ski-E-O build** (shown at the bottom of the main menu, e.g. "Build: CL8231").
The newest Together release is made for **CL8231** (the Ski-E-O update of November 21, 2026).
Together refuses to connect different builds and tells you why. Saves made by a newer build can't be opened by an older one.
Walkway, brush, tree and terrain sharing, copying friends' lifts and building variants, and lift linking use a small native module (`Native\TogetherNative.dll`) matched
to the game's code. If a game update changes that code, the log says so and those fall back to being shared through a
refresh; everything else keeps working.

## Install (everyone)

Together is shared directly (not through the Mod Manager catalog, which doesn't allow its network helper program).

1. Needs UE4SS 4 (the same setup Z's Mod Manager installs for other Ski-E-O mods; installing any catalog mod once
   sets it up) and Steam running.
2. Close the game. Unzip so that the `SkiEOTogether` folder lands in `...\steamapps\common\skieogame\mods\`
   (you should end up with `mods\SkiEOTogether\mod.ini`). Updating: replace the folder; keep its `bridge` folder if
   you want to keep your name and password settings.
3. **Everyone must use the same Together version and the same Ski-E-O build** (the main menu shows the build).
4. A small helper (`Network\TogetherNet.exe`) starts in the background with the game and closes ~20 s after it;
   while it runs, Steam shows Ski-E-O as running. Windows may ask to allow it through the firewall the first time
   you host: allow it, or friends can't connect. It isn't code-signed, so some antivirus tools may ask about it.
   Ctrl+F9 outside a menu opens its diagnostics window.

## Good to know

- Your own resorts are never touched by joining; the host's own save is written at each refresh.
- Guests walking around are simulated by each game separately, so crowds differ between refreshes.
- **Ctrl+F10** moves your camera to the next player.
- Logs for bug reports: `SkiEOTogether\bridge\together.log`.

## Uninstall

Delete the `SkiEOTogether` folder. Joined copies are the `Together_*` saves in
`%LOCALAPPDATA%\Ski-E-O\SaveGames` (delete them while the game is running, or Steam Cloud restores them).

