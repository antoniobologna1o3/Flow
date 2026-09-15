# Compatibility

## Reign of Nether

Netherfront is designed as a companion to Reign of Nether, but it is **not
coupled to it**.

### The problem

Reign of Nether publishes no documented mod API — no events, no capabilities, no
integration hooks. Building against its internals directly would mean:

- a hard compile-time dependency, so Netherfront could not load without it, and
- a mod that breaks every time Reign of Nether refactors.

### What Netherfront does instead

The integration lives entirely in
`com.netherfront.integration.reignofnether.RonBridge`, and:

1. Checks whether the `reignofnether` mod id is loaded at all.
2. If it is, resolves the few things it needs **by reflection, once, at startup**.
3. If any of that fails, logs a single clear warning and disables the RTS-specific
   integration.

There is **no compile-time dependency** on any Reign of Nether class. The mod
loads and plays standalone.

Every call site has defined behaviour when the bridge is inactive — nothing in
Netherfront's own systems depends on the bridge returning a value.

### The three states

| State | Meaning | Behaviour |
|---|---|---|
| Not installed | `reignofnether` is absent | All companion systems run normally. Teams assigned manually or auto-balanced. |
| Present, manual teams | Installed, but its unit ownership accessor was not found | Everything works; Netherfront just cannot read RoN unit ownership. Assign teams with `/netherfront team assign`. |
| Integrated | Installed and reflection resolved | As above, plus Netherfront can identify the owner of a Reign of Nether unit. |

Check which state you are in:

```
/netherfront status
```

The startup log also records it.

### Binding teams to RoN factions

When you want a Netherfront team to correspond to a Reign of Nether faction:

```
/netherfront team ronfaction team_a villagers
```

This is a hint used by the integration layer, and is safe to set even when
Reign of Nether is not installed.

### If Reign of Nether updates and integration breaks

Nothing crashes. The bridge fails to resolve, logs a warning once, and the mod
continues in "present, manual teams" mode. Teams then need assigning by command,
which is the same path used when Reign of Nether is absent entirely.

---

## Other mods

Netherfront deliberately avoids the things that usually cause mod conflicts:

- **No custom entity types.** Scouts, spies and mercenaries are ordinary vanilla
  mobs carrying a persistent-data tag. This specifically avoids clashing with
  Reign of Nether's own unit entities.
- **No mixins**, and no modification of vanilla behaviour beyond a small number
  of standard Forge event listeners.
- **No worldgen registration.** Relic sites and mercenary camps are placed by the
  mod at runtime, not injected into the world generator, so Netherfront does not
  interfere with other mods' structures or biome generation.
- **Three blocks and two items**, all under the `netherfront:` namespace.

### Event listeners used

| Event | Why |
|---|---|
| `ServerTickEvent` | Scheduled subsystem updates |
| `ServerStartedEvent` / `ServerStoppingEvent` | Match lifecycle and save flush |
| `PlayerLoggedInEvent` / `PlayerLoggedOutEvent` | Late join and reconnect handling |
| `RegisterCommandsEvent` | The `/netherfront` command tree |
| `BlockEvent.EntityPlaceEvent` / `BreakEvent` | Tracking strategic structures |
| `LivingDeathEvent` | Village reputation, boss kills, combat statistics |
| `LivingHealEvent` | Suppressing regeneration while out of supply |

The heal listener cancels only heals of 1.0 or less, which is what vanilla
natural regeneration and the Regeneration effect tick for. Potions and food heal
more and are unaffected, so healing still works while cut off. If another mod
adds a healing source that ticks for exactly 1.0, it will also be suppressed
while a player is unsupplied.

### Performance

- No subsystem runs every tick; each declares its own interval and they are
  staggered so systems sharing an interval do not fire together.
- **Nothing force-loads chunks.** Village discovery uses points of interest near
  players; relic sites and mercenary camps choose only X and Z up front and build
  themselves the first time a player comes close.
- A structure, boss or spy in an unloaded chunk is treated as *unknown*, never
  *destroyed*.
- Territory ownership is computed on demand from a list of sources (dozens) and
  cached per update, rather than maintained as a world-sized grid.
- Per-team explored-chunk memory is capped and pruned in amortised batches.
