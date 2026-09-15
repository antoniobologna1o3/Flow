# Netherfront: Dynamic Warfare

A companion expansion for the [Reign of Nether](https://www.curseforge.com/minecraft/mc-mods/reign-of-nether-rts-in-minecraft)
RTS mod for **Minecraft 1.20.1 (Forge)**.

Netherfront does not replace or reimplement Reign of Nether's RTS gameplay. It
adds the strategic layer *around* a match — exploration, territory, villages,
relics, supply lines, espionage, dynamic objectives, world events and world
bosses — so that a match is decided by more than a straight fight between two
bases.

It never touches Reign of Nether's units, buildings or economy.

---

## Why Forge and not Fabric

Reign of Nether ships for **Forge only** on 1.20.1. Fabric and Forge cannot run
in the same instance, so a Fabric build of this mod could never load alongside
it. Netherfront therefore targets Forge 1.20.1 (47.4.9).

Netherfront also runs perfectly well **without** Reign of Nether installed, with
the RTS integration disabled. Teams are then assigned manually.

---

## Installation

1. Install **Minecraft 1.20.1**.
2. Install **Forge 1.20.1** (47.x — built and tested against 47.4.9).
3. *(Optional but recommended)* Install **Reign of Nether RTS** for 1.20.1.
4. Drop `netherfront-<version>.jar` into your `mods/` folder.
5. Start the world or server.
6. Configure the match (see below) and play.

**Dependencies**

| Mod | Required | Notes |
|---|---|---|
| Forge 1.20.1 (47+) | Yes | |
| Minecraft 1.20.1 | Yes | |
| Reign of Nether RTS | No | Optional. Enables RTS integration when present. |

### Building from source

```bash
./gradlew build      # jar lands in build/libs/
./gradlew test       # pure-logic unit tests
```

Requires a JDK 17 toolchain.

---

## Setting up a match

```
/netherfront status                          # what's running right now
/netherfront match mode DYNAMIC_WAR          # pick a mode (resets system toggles)
/netherfront team assign <player> team_a     # put players on teams
/netherfront match system spies false        # turn an individual system off
/netherfront match start
```

By default the match auto-starts when the first player joins and unassigned
players are auto-balanced onto the smallest team. Both are configurable.

### Game modes

| Mode | What it does |
|---|---|
| `CLASSIC` | Everything off except basic world events. |
| `DYNAMIC_WAR` | All systems active. The default. |
| `SURVIVAL_WAR` | PvP plus escalating PvE pressure. |
| `RELIC_WAR` | Relics are the primary objective. |
| `DOMINATION` | Hold strategic locations to accumulate points and win on score. |
| `CAMPAIGN` | Reserved for scripted scenarios (**not yet implemented** — see below). |
| `CUSTOM` | Every system on; tune by hand. |
| `HARDCORE` | Dynamic War with higher PvE pressure and harsher penalties. |

A mode is just a default set of system toggles plus a victory rule, so any mode
can be tuned freely afterwards with `/netherfront match system`.

**Only `DOMINATION` ever declares a winner.** In every other mode Netherfront
never ends the match — Reign of Nether stays the thing that decides the game.

---

## The systems

### Territory
Influence radiates from your structures, allied villages and held relics,
falling off with distance. Territory is **never a barrier** — you can always
walk into enemy ground, which is what makes raiding and infiltration possible.
It only decides supply, vision, village attitude and scoring.

### Fog of war and intelligence
Knowledge has five levels, from unknown up to confirmed, and decays with time.
Once your team has been somewhere you remember the terrain permanently; you just
stop knowing what is happening there.

Enemy positions are recorded as **sightings** — where something was when you last
saw it. They go stale on their own and are deliberately capped below "confirmed",
so the map can never imply live tracking of an enemy you cannot see.

Vision penalties multiply, so a foggy night in a forest is genuinely blinding.

> All of this is filtered **server-side**. The only gameplay data a client ever
> receives is a per-team snapshot the server has already censored, so a modified
> client cannot reveal what it was not told.

### Scouting
Use **Scout Orders** on any mob to turn it into a scout: much wider vision,
faster, and no tougher than it was. Losing one to a defended position is the
intended cost.

### Supply lines
Supply spreads from **roots** (Supply Depots, and villages that side with you)
and is carried further by **Outposts** — but only outposts that are themselves
already in supply. Take the middle outpost of a chain and everything past it
goes dark.

Being unsupplied is a drag, not a death sentence: mild slowness, weakness, and
suppressed natural regeneration. A team that has built nothing is not penalised
at all.

### Dynamic villages
Villages are found by looking for village bells near players, and each simulates
food, security and prosperity, with its own personality (Farming, Mining,
Trading, Military, Port, Ancient).

Reputation is tracked **per team**, so the same village can be your ally and your
opponent's enemy. Villages issue procedural requests; the first team to make
progress claims one, so both sides can never be paid for the same job. Deliver
goods by dropping them at the village meeting point.

Villages are never invincible — they can still be attacked and taken.

### Relics
Six relic types, each granting a small, non-stacking passive to the holding team.
Sites generate away from spawn and build their altar the first time a player
comes near.

Capture requires an **uncontested** hold: while two teams are both standing on a
site, nobody makes progress. Contesting is always worth doing.

### Espionage
Use **Spy Orders** on a mob to send it spying. A spy in enemy territory reveals
ground around it and files a report if it survives long enough.

Detection is two-stage on purpose. The defender first gets a vague warning at a
*blurred* position, then the spy is exposed. The defender gets a chance to react
and the attacker gets a chance to withdraw. Watchtowers are the main counter.

Spies are **information only** — no sabotage. Espionage never bypasses the RTS
fight.

### Mercenaries
Six contingent types at neutral camps. Hire by leaving payment at the camp, so
the fee is a real item stack an enemy could have taken from you first. Hired
units use vanilla combat AI and will never turn on the team that paid.

Camps restock on a timer, so they supplement an army rather than replacing one.

### Dynamic objectives and world events
Objectives track progress per team so both sides race for the same prize, and
they are always optional — ignoring every one is a legitimate strategy.

The event director weights its table against the current state of the match,
damps repeats, and enforces a hard minimum gap so events never become noise.
Every event is bounded in area and time.

### World bosses
Neutral, optional, and placed well away from bases. Fighting one means pulling
an army off the front line, which is the real cost. Rewards are supplies, not
power.

### Statistics and achievements
Per-team counters across exploration, military, economy, intelligence,
objectives and world activity, shown as a war summary when a match ends.
Achievements award nothing, so they cannot affect balance.

---

## Building things

| Item | Recipe | Purpose |
|---|---|---|
| Forward Outpost | Wool + sticks | Territory, vision, and **relays** supply |
| Supply Depot | Planks + chest | **Root** of a supply network |
| Watchtower | Stone bricks, glass, lantern | Big vision radius; detects spies |
| Scout Orders | Paper + feather | Turns a mob into a scout |
| Spy Orders | Paper + ender pearl | Sends a mob spying |

Structures are ordinary blocks — an enemy destroys one by breaking it, with no
special rules.

---

## Commands

Read-only commands are open to everyone but **filtered to what your team has
discovered**. The unfiltered view and anything that changes state require
permission level 2, so the command tree cannot be used to sidestep fog of war.

```
/netherfront status | feed | stats
/netherfront match start | end <team> | reset
/netherfront match mode <mode>
/netherfront match system <system> <true|false>
/netherfront match eventfrequency <DISABLED|LOW|NORMAL|HIGH>
/netherfront match dominationtarget <score>
/netherfront team list | assign <player> <team> | unassign <player>
/netherfront team ronfaction <team> <faction>
/netherfront relic list
/netherfront village list | info
/netherfront objective list | complete <team>
/netherfront event list | stop
/netherfront boss spawn <type>
/netherfront supply debug
/netherfront spy debug
/netherfront debug <true|false>
/netherfront reload
```

---

## Configuration

Everything is configurable. See **[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**.

- `config/netherfront-server.toml` — all gameplay. Server-authoritative.
- `config/netherfront-client.toml` — HUD, sounds, and accessibility only.

Accessibility options include colourblind glyphs (ownership is never shown by
colour alone), reduced animation, fog intensity and notification frequency.

---

## Reign of Nether compatibility

See **[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)**.

In short: Reign of Nether publishes no documented mod API, so Netherfront does
not pretend one exists. It resolves what it needs by reflection once at startup
and degrades cleanly to manual team assignment if anything is missing or has
moved. **It never crashes because Reign of Nether is absent or has changed.**

Check what happened with `/netherfront status`.

---

## What is not implemented

The design brief this was built from is large. These parts are **not** built,
and are listed here rather than stubbed out as fake buttons:

- **Naval system** (transport/scout/warships, naval bases, coastal defence).
- **Underground warfare and secret tunnels** (bunkers, hidden routes, tunnel
  networks).
- **Caravans as a full system.** A merchant caravan can appear as a world event,
  but there is no escort/destination/prosperity loop, and the matching
  `ESCORT` objective and `PROTECT_CARAVAN` village request are defined but never
  generated.
- **Campaign mode and the custom scenario file format.** `CAMPAIGN` is selectable
  but currently enables no systems.
- **Spy sabotage.** Espionage is deliberately information-only for now.
- **Terrain fog rendering on the strategic map.** Markers are fogged correctly and
  faded by intel level, but explored terrain itself is not drawn — the map shows
  a grid, not a rendered world.

### Testing status

`gradle build` and `gradle test` both pass (33 unit tests over the pure logic:
weighted selection, reputation bands, intel decay, territory falloff, objective
claiming, deterministic village naming, cooldown persistence).

**The mod has not yet been launched in a running game.** Everything above is
implemented against the Forge 1.20.1 API and compiles into a loadable jar, but
in-game verification — and the balance tuning that can only come from actually
playing it — is still outstanding.

---

## License

MIT.
