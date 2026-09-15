# Configuration

Netherfront has two config files, created on first run:

| File | Scope | Authority |
|---|---|---|
| `config/netherfront-server.toml` | All gameplay | **Server.** On a dedicated server this is the only thing that matters. |
| `config/netherfront-client.toml` | HUD, sound, accessibility | Per-client. Never affects gameplay. |

Config values are the **defaults a new match starts from**. To change something
mid-match, use `/netherfront match ...` — those in-match settings are saved with
the world, so they survive a restart.

After editing a file, run `/netherfront reload` to push refreshed state to
clients.

---

## Server config

### `[match]`

| Key | Default | Meaning |
|---|---|---|
| `defaultMode` | `DYNAMIC_WAR` | Mode a brand-new match starts in. |
| `autoStartMatch` | `true` | Start automatically when the first player joins. |
| `autoAssignTeams` | `true` | Put unassigned players on the smallest team. |

### `[territory]`

| Key | Default | Meaning |
|---|---|---|
| `updateIntervalTicks` | `100` | How often influence is recalculated. |
| `maxRadiusChunks` | `12` | Ceiling on any single influence radius. |

### `[intel]`

| Key | Default | Meaning |
|---|---|---|
| `updateIntervalTicks` | `40` | How often fog of war updates. |
| `decayTicks` | `2400` | How long observed ground stays "recently observed". |
| `armyVisionChunks` | `4` | Vision radius for a normal player. |
| `scoutVisionChunks` | `8` | Vision radius for a scout. |
| `watchtowerVisionChunks` | `10` | Vision radius for a watchtower. |
| `nightPenalty` | `0.6` | Vision multiplier at night. |
| `stormPenalty` | `0.7` | Vision multiplier in rain or thunder. |
| `forestPenalty` | `0.75` | Vision multiplier in forest biomes. |

Penalties **multiply**. A scout at night, in a storm, in a forest sees
`8 × 0.6 × 0.7 × 0.75 ≈ 2.5` chunks. Raise these toward `1.0` for a less
punishing game.

### `[supply]`

| Key | Default | Meaning |
|---|---|---|
| `updateIntervalTicks` | `100` | How often the network is rebuilt. |
| `sourceRadiusChunks` | `10` | Reach of a root (depot or allied village). |
| `relayRadiusChunks` | `6` | Reach added by an outpost relay. |
| `speedPenalty` | `0.85` | Movement multiplier while unsupplied. |
| `damagePenalty` | `0.9` | Damage multiplier while unsupplied. |
| `blocksRegen` | `true` | Suppress natural regeneration while unsupplied. |

Set the penalties to `1.0` and `blocksRegen` to `false` to make supply purely
informational.

### `[villages]`

| Key | Default | Meaning |
|---|---|---|
| `scanIntervalTicks` | `600` | How often villages are looked for near players. |
| `maxTracked` | `32` | Hard cap on tracked villages. Raise with care. |
| `requestIntervalTicks` | `6000` | Average gap between a village's requests. |
| `requestExpiryTicks` | `12000` | How long an unclaimed request lasts. |
| `reputationMax` | `100` | Reputation scale (±). |

### `[relics]`

| Key | Default | Meaning |
|---|---|---|
| `count` | `5` | Relic sites per match. `0` disables generation. |
| `minSpacingBlocks` | `400` | Minimum distance between two sites. |
| `captureTicks` | `1200` | Uncontested hold needed to capture (60s). |
| `spawnRadiusBlocks` | `1500` | Radius around spawn used for placement. |
| `globalDiscoveryAnnounce` | `false` | Tell everyone when any relic is found. |

### `[mercenaries]`

| Key | Default | Meaning |
|---|---|---|
| `campCount` | `6` | Camps per match. |
| `respawnTicks` | `9000` | Restock delay after a camp is bought out. |

### `[objectives]`

| Key | Default | Meaning |
|---|---|---|
| `intervalTicks` | `4800` | Average gap between new objectives. |
| `maxActive` | `3` | Maximum active at once. |

### `[events]`

| Key | Default | Meaning |
|---|---|---|
| `intervalTicks` | `9000` | Average gap at `NORMAL` frequency. |
| `minGapTicks` | `3600` | **Hard** floor between events, whatever the frequency. |
| `maxConcurrent` | `2` | Maximum simultaneous events. |

`minGapTicks` is the anti-spam guarantee: raising event frequency shortens the
average gap but can never breach this floor.

### `[bosses]`

| Key | Default | Meaning |
|---|---|---|
| `intervalTicks` | `24000` | Average gap between boss spawns. |
| `maxActive` | `1` | Maximum live bosses. |
| `healthMultiplier` | `1.0` | Scales boss health on top of its own multiplier. |

### `[spies]`

| Key | Default | Meaning |
|---|---|---|
| `updateIntervalTicks` | `40` | How often spies and detection update. |
| `detectionChance` | `0.12` | Chance per check of a large suspicion gain. |
| `detectionRadius` | `24` | Detection range in blocks (doubled for watchtowers). |
| `missionDurationTicks` | `3600` | How long before a spy can file a report. |

### `[weather]`

| Key | Default | Meaning |
|---|---|---|
| `affectsCombat` | `true` | Weather influences vision and events. |
| `snowSlowFactor` | `0.9` | Movement multiplier in deep snow. |

### `[performance]`

| Key | Default | Meaning |
|---|---|---|
| `onlyTickLoadedChunks` | `true` | Never force-load chunks for companion systems. |
| `maxStructureSearchChunks` | `128` | Ceiling on any structure search radius. |

Leave `onlyTickLoadedChunks` on unless you are debugging.

---

## Client config

### `[hud]`

| Key | Default | Meaning |
|---|---|---|
| `showEventFeed` | `true` | Show the rolling event feed. |
| `eventFeedLines` | `5` | Visible feed lines. `0` hides it. |
| `eventFeedHoldTicks` | `200` | How long a notification stays up. |
| `showToasts` | `true` | Show notification popups. |
| `playEventSounds` | `true` | Play notification sounds. |
| `showWorldMarkers` | `true` | Show map markers. |

### `[accessibility]`

| Key | Default | Meaning |
|---|---|---|
| `colorblindMode` | `false` | Always draw ownership glyphs alongside colour. |
| `reducedAnimation` | `false` | Disable pulsing and animated effects. |
| `fogIntensity` | `0.8` | How dark unexplored map areas are drawn. |

Ownership is **never** communicated by colour alone — every team has a distinct
glyph and every feed category has its own symbol, regardless of this setting.
`colorblindMode` makes the glyphs more prominent.

---

## Tuning recipes

**"Events are too frequent"**
`/netherfront match eventfrequency LOW`, or raise `events.minGapTicks`.

**"Fog of war is too harsh"**
Raise `intel.decayTicks` so knowledge stays fresh longer, and move the vision
penalties toward `1.0`.

**"Supply is frustrating"**
Raise `supply.sourceRadiusChunks` and `relayRadiusChunks`, or set the penalties
to `1.0` to make supply informational only.

**"I want a pure RTS match with light flavour"**
`/netherfront match mode CLASSIC`.

**"Performance on a busy server"**
Raise every `updateIntervalTicks`, lower `villages.maxTracked`, and lower
`relics.count` and `mercenaries.campCount`.
