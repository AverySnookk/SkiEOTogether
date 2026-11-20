# Blockside Resort

A ski-resort management mod for **Minecraft Java 26.3 / NeoForge / JDK 25**. Survey a mountain, cut runs,
buy projects with resort cash, hire crews to build them in visible stages, make snow, groom, open lifts, and
then ride the chairlift and ski the terrain you made. One shared resort per world, in single-player or on a
dedicated server with friends.

Everything here (code, block templates, textures, UI) is original.

## Layout

| Path | What |
|---|---|
| `core/` | Plain-Java resort simulation, no Minecraft dependency: ledger, XP milestones, reputation factors, data-driven catalog + templates, construction zones/crews/jobs, lift planning and the chair loop, trail surface design, earthwork journal, snowpack + weather, ski physics, guest model. Unit-tested. |
| `core/src/main/resources/blockside/` | `catalog.json`, `resort_config.json`, `templates/*.json`: all prices, levels, milestones, rates and building layouts. |
| `neoforge/` | The mod: blocks, items, `/resort` commands, chat panels + Resort Tablet screen + HUD, construction in the world, chairlift, snowcat, snow gun, client skiing, test hill. Compiles `core` straight into the jar. |
| `tools/` | `make_templates.py` and `make_assets.py` regenerate the building templates and the 16×16 textures/models. |

## Build and run

```bash
cd BlocksideResort
./gradlew -PcoreOnly :core:test     # simulation tests only (JDK 21+, no Minecraft download)
./gradlew :neoforge:build           # mod jar → neoforge/build/libs/blockside-resort-0.1.0.jar
./gradlew :neoforge:runClient       # dev client (single-player)
./gradlew :neoforge:runServer       # dev dedicated server (accept the EULA in neoforge/run/eula.txt)
```

The mod build needs `maven.neoforged.net` and Mojang's servers. If you have no JDK 25, Gradle downloads one
through the foojay resolver (or point Gradle at one: `-Porg.gradle.java.installations.paths=/path/to/jdk25`).
Built and tested with NeoForge `26.3.0.115` (`neo_version` in `gradle.properties`).

Testing on a dedicated server with nobody online: Minecraft pauses ticking 60 s after the last player leaves,
which also pauses the resort. Set `pause-when-empty-seconds=-1` in `server.properties` for unattended tests.
Console-only test commands (server console or RCON): `/resort selftest <x> <y> <z>` builds the test hill and buys,
places and hires the ticket booth, groomer garage and chairlift, plus a running snow gun; `/resort status` prints the
resort; `/resort console openlift|groom|skip <h>|save`. Force-load the area first
(`/forceload add -48 -96 48 48` for a selftest at 0 80 0).

To install, drop `blockside-resort-0.1.0.jar` into `mods/` on the server and on every client.

## Playtest path (about 10 minutes)

Use a **test world**. The test hill replaces a 61×105 area.

1. Creative, flat-ish snowy ground. `/resort claim` → you get a **Resort Tablet**, **Skis** and a **Survey Stake**.
   (Or skip the claim: the test hill claims for you.)
2. `/resort testhill` → click **[Build the test hill here]**. Uphill is north. You get:
   *Bunny Hill* (1 down per 3 forward ≈ 18°, open, packed but ungroomed base), *Expert Wall* (45°),
   *The Chute* (≈63°, flagged as a cliff hazard), and a flat base area. Chat prints suggested coordinates.
3. `/resort catalog` (or the Tablet → Catalog) → **Buy** *Ticket Booth*. You get a Project Kit.
4. Right-click the ground at the suggested spot → green outline = valid (flames = blocked, with the reason in
   chat), blue flame column = entrance. Click the same spot again to rotate. **[Confirm]**.
5. Right-click the yellow-striped center block → **[Hire crew $…]**. A crew member appears and the booth goes
   up in stages: site prep → foundation → structure → equipment. Completion is announced with what it adds.
6. Buy the *Beginner Double Chair*. Right-click the bottom station spot, then the top station spot. End-rod
   particles trace the cable, cloud columns mark towers, and smoke marks chair-clearance problems. **[Confirm]**
   → hire at the lower station's center block. Towers and both stations get built.
7. Right-click the lift's center block → **[Open lift]**. Stand on the yellow mark in the bottom station; a
   chair picks you up and lets you off at the summit.
8. Right-click the Skis to clip in and ski Bunny Hill. W = tuck, S = snowplough brake, A/D = carve,
   Ctrl = dig edges in, Space = hop, mouse = where the skis point. The HUD shows speed and surface type.
9. Buy a *Snow Gun* (delivered as an item). Place it facing down the run, with a water source within 4 blocks
   and a redstone signal. Right-click to turn it on; it explains if it's too warm, dry or unpowered.
   Snow guns only run at or below −2 °C: `/resort admin season winter` or `/resort admin weather cold 12`.
10. Buy and build a *Groomer Garage* → its center block → **[Drive groomer]** (or `/resort groomer` in
    creative). W/S throttle, A/D steer, **Space** lowers the tiller, Shift parks. The groomed snow shows corduroy
    and is firm (you don't sink), so your next run is faster than on powder.
11. Time: `/resort admin skip 6` simulates the missed hours. Guests buy tickets once the resort has
    an open ticket booth, an open lift and an open run. The first $1,000 of guest revenue pays XP; booth + lift
    + revenue reach **level 2**, which unlocks the *Rental Shop* and *Small Lodge*. The lodge also needs
    reputation 55, so your reputation is a second unlock gate. *Reputation 60 held for 2 hours* is the
    reputation XP milestone.
12. `/stop` the server (or save and quit), start again: cash, XP, jobs mid-stage, lifts, runs and snow are
    restored from `world/blockside/resort.json`, and a half-built project resumes at its saved stage.

## Controls

| Item / key | Action |
|---|---|
| Resort Tablet (right-click) | Management screen: Dashboard (cash, level, XP to next, reputation and each factor ▲/▼, open terrain, ticket sales, guests, lift wait, weather, alerts, jobs, milestones), Catalog, Jobs, Runs & Lifts |
| Project Kit (right-click ground) | Preview footprint; again on the same spot = rotate. Lifts: bottom then top station |
| Center block (right-click) | Job: quote, crew availability, hire, progress. Done: facility/lift panel |
| Survey Stake | Right-click = add a centerline stake (shows the grade from the previous one); sneak + right-click = clear |
| Skis (right-click) | Clip in/out. W tuck · S brake · A/D carve · Ctrl edges · Space hop |
| Snow Gun (right-click) | On/off + status |
| Snowcat | W/S throttle · A/D steer · Space tiller · Shift park |

## Commands

| Command | |
|---|---|
| `/resort` | Dashboard in chat |
| `/resort claim`, `/resort kit` | Claim the resort area; give Tablet, Skis and Stake |
| `/resort catalog`, `/resort buy <id>`, `/resort refund` | Catalog, buy, refund the unplaced kit in hand |
| `/resort place confirm\|rotate\|reset\|cancel` | Placement preview actions (the chat buttons run these) |
| `/resort hire <job>`, `/resort jobs` | Hire a crew (within 32 blocks of the site), list jobs |
| `/resort lifts`, `/resort lift open\|close <id>` | Lifts |
| `/resort trail preview [width]` | Grade per segment, difficulty, trees, GAP/CLIFF/PROTRUDING flags (particles) |
| `/resort trail confirm <clear\|fix\|grade> <width> <name>` | Register the run and queue batched earthwork |
| `/resort trail undo <job>`, `/resort trail list\|open\|close <name>` | Undo journal; run status |
| `/resort facility toggle <id>`, `/resort groomer`, `/resort snow` | Facility open/closed; snowcat; snowpack at your feet |
| Creative only: `/resort testhill`, `/resort save`, `/resort admin cash\|xp\|rep\|weather <storm\|warm\|cold\|clear> [h]\|skip <h>\|season <autumn\|winter\|spring\|summer>` | Test tools |

## How the systems fit

- **Cash, reputation and XP are separate.** Cash is the ledger: purchases, crew labor, wages, lift power and
  snowmaking water cost money, and guest tickets and rentals earn it. Reputation is a 0–100 blend of five
  visible factors: snow quality on open runs, lift lines, open terrain, facilities, and reliability. XP
  belongs to the resort, and every award has a unique id, so it pays once.
  Revenue XP counts guest transactions only, so buy/refund spam is worth nothing. Construction XP is keyed
  by catalog entry, so rebuilding pays nothing. Reputation XP needs the threshold held continuously; a
  dip resets the timer, and once paid it never pays again. Awards are evaluated on the server from resort
  state, so a reconnect can't double them.
- **Construction:** purchase → kit → validated zone (claim, overlaps, protected player builds, fluids, unsupported
  ground, slope; trees are fine because the crew clears them) → job block → hire (idempotent: double-clicks and two
  players at once charge once) → staged placements, all inside the approved footprint, regenerated
  deterministically on load → facility or lift registered.
- **One shared surface:** a run's design surface is relaxed over the ground samples (never below ground,
  ≤ 1.25 m above it), so snow fills a 1:3 staircase into a smooth 18° plane. The snowpack is drawn as 1/8-block snow layers. The
  skier (client) samples those rendered heights bilinearly for height + normal; the groomer drives on them;
  grooming pushes snow toward the design surface.
- **Snowpack per column:** SWE, density, retained water, ice fraction, groom freshness; depth = mass ÷ density.
  Storms add powder, warm hours melt (sun-facing slopes faster), retained water refreezes to ice overnight,
  grooming compacts without changing mass. Snow runs on a slow clock (every 100 ticks); unloaded tiles catch
  up hour-by-hour against the deterministic weather when their chunk loads.
- **Server-authoritative:** money, XP, reputation, placement, jobs, weather and snow live on the server. Clients
  send `/resort` commands (validated for membership, range, cash and state) and receive one shared snapshot.
  Only ski movement runs client-side, for responsiveness.

Tuning: the server writes `world/blockside/resort_config.json` and `catalog.json` on first start. Edit them
(prices, levels, milestones, melt rates, gun cutoff, crew speed…) and restart. Put a file in
`world/blockside/templates/` to override a building template.

## Status

**Verified here:** `./gradlew -PcoreOnly :core:test` passes all 35 tests. They cover:
overlapping zones, double-click/concurrent hire, save + reload mid-job (resumes at the same stage, no
re-charge), XP once (revenue spam, rebuild, reputation dips, reload), illegal placements (claim, protected builds,
water, cliff edge, membership), melt stopping at zero with mass accounted, two players getting identical
snapshots, the 18°/45°/63° fixtures, grooming mass conservation, snow gun statuses, unloaded catch-up, ski
acceleration/terminal speed/braking/powder vs groomed/ice grip, lift clearance and loop continuity, a
full lift job, and the level-2 unlock. The Gradle project configures with ModDevGradle.

**Verified on a real 26.3 dedicated server** (NeoForge 26.3.0.115, JDK 25), driven via RCON with `/resort selftest`:
the mod loads; the test hill builds (~100k blocks) with its three trails; ticket booth, groomer garage and
chairlift are bought, validated, placed and hired; a second hire click charged nothing; crews build every
stage and the booth, garage, lift stations and 5 towers appear in the world; the lift registers; guests buy
tickets (revenue, rides, reputation 50 → 70); the revenue, construction, first-lift and reputation-60 milestones
pay once and the resort reaches level 2; the snow gun runs; the snowpack renders as snow layers and grooming turns
Bunny Hill into corduroy blocks while keeping it 100% covered; jobs restarted mid-stage resume where they left
off; after a server restart cash, XP, reputation, milestones, snow and rendered blocks are identical.

**Not verified yet (needs a player client):** the Resort Tablet screen and HUD, kit placement preview clicks,
riding the chairlift (chairs only spawn with a player nearby), driving the snowcat, and client-side skiing.
These compile against 26.3; the reflective calls in `Compat.java` were checked against the 26.3 sources.

**Next steps, in order:**
1. Walk the playtest path in a client (`./gradlew :neoforge:runClient`) and tune ski/lift feel.
2. NeoForge GameTests for the in-world parts (zone placement, crew build, lift boarding, snow rendering).
3. Visual placement preview with block outlines instead of particles; visible zone fencing.
4. Crew-assigned grooming, lift queues with visible guests, guest pathing over a graph of paths/buildings/runs.
5. Pump house, power and water networks; templates for the remaining catalog entries (ski school,
   patrol hut, base lodge, quad, gondola…).
6. Humidity/wind for snowmaking, forecasts, bigger generated mountains and heightmap import.
