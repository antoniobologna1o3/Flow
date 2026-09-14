package com.netherfront.common.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Server-side configuration (section 37). Everything that affects gameplay
 * lives in the SERVER config so a dedicated server is the single authority;
 * the CLIENT config holds presentation and accessibility only (section 44).
 */
public final class NFConfig {
    private NFConfig() {}

    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();

        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    public static final class Server {
        public final ForgeConfigSpec.ConfigValue<String> defaultMode;
        public final ForgeConfigSpec.BooleanValue autoStartMatch;
        public final ForgeConfigSpec.BooleanValue autoAssignTeams;

        // Territory
        public final ForgeConfigSpec.IntValue territoryUpdateInterval;
        public final ForgeConfigSpec.IntValue territoryChunkRadius;

        // Intel / fog of war
        public final ForgeConfigSpec.IntValue intelUpdateInterval;
        public final ForgeConfigSpec.IntValue intelDecayTicks;
        public final ForgeConfigSpec.IntValue baseUnitVisionChunks;
        public final ForgeConfigSpec.IntValue scoutVisionChunks;
        public final ForgeConfigSpec.IntValue watchtowerVisionChunks;
        public final ForgeConfigSpec.DoubleValue nightVisionPenalty;
        public final ForgeConfigSpec.DoubleValue stormVisionPenalty;
        public final ForgeConfigSpec.DoubleValue forestVisionPenalty;

        // Supply
        public final ForgeConfigSpec.IntValue supplyUpdateInterval;
        public final ForgeConfigSpec.IntValue supplySourceRadiusChunks;
        public final ForgeConfigSpec.IntValue supplyRelayRadiusChunks;
        public final ForgeConfigSpec.DoubleValue unsuppliedSpeedPenalty;
        public final ForgeConfigSpec.DoubleValue unsuppliedDamagePenalty;
        public final ForgeConfigSpec.BooleanValue unsuppliedBlocksRegen;

        // Villages
        public final ForgeConfigSpec.IntValue villageScanInterval;
        public final ForgeConfigSpec.IntValue villageMaxTracked;
        public final ForgeConfigSpec.IntValue villageRequestIntervalTicks;
        public final ForgeConfigSpec.IntValue villageRequestExpiryTicks;
        public final ForgeConfigSpec.IntValue villageReputationMax;

        // Relics
        public final ForgeConfigSpec.IntValue relicCount;
        public final ForgeConfigSpec.IntValue relicMinSpacingBlocks;
        public final ForgeConfigSpec.IntValue relicCaptureTicks;
        public final ForgeConfigSpec.IntValue relicSpawnRadiusBlocks;
        public final ForgeConfigSpec.BooleanValue relicGlobalDiscoveryAnnounce;

        // Mercenaries
        public final ForgeConfigSpec.IntValue mercenaryCampCount;
        public final ForgeConfigSpec.IntValue mercenaryRespawnTicks;

        // Objectives
        public final ForgeConfigSpec.IntValue objectiveIntervalTicks;
        public final ForgeConfigSpec.IntValue objectiveMaxActive;

        // Events
        public final ForgeConfigSpec.IntValue eventIntervalTicks;
        public final ForgeConfigSpec.IntValue eventMinGapTicks;
        public final ForgeConfigSpec.IntValue eventMaxConcurrent;

        // Bosses
        public final ForgeConfigSpec.IntValue bossIntervalTicks;
        public final ForgeConfigSpec.IntValue bossMaxActive;
        public final ForgeConfigSpec.DoubleValue bossHealthMultiplier;

        // Spies
        public final ForgeConfigSpec.IntValue spyUpdateInterval;
        public final ForgeConfigSpec.DoubleValue spyDetectionChance;
        public final ForgeConfigSpec.IntValue spyDetectionRadius;
        public final ForgeConfigSpec.IntValue spyMissionDurationTicks;

        // Weather
        public final ForgeConfigSpec.BooleanValue weatherAffectsCombat;
        public final ForgeConfigSpec.DoubleValue snowSlowFactor;

        // Performance
        public final ForgeConfigSpec.BooleanValue onlyTickLoadedChunks;
        public final ForgeConfigSpec.IntValue maxStructureSearchRadius;

        Server(ForgeConfigSpec.Builder b) {
            b.comment("Netherfront: Dynamic Warfare - server settings.",
                    "Every gameplay calculation is server-authoritative; these values are the defaults",
                    "a new match starts from. In-match overrides are made with /netherfront match set.").push("match");
            defaultMode = b.comment("Default match mode: CLASSIC, DYNAMIC_WAR, SURVIVAL_WAR, RELIC_WAR, DOMINATION, CAMPAIGN, CUSTOM, HARDCORE")
                    .define("defaultMode", "DYNAMIC_WAR");
            autoStartMatch = b.comment("Start the match automatically when the first player joins.")
                    .define("autoStartMatch", true);
            autoAssignTeams = b.comment("Automatically place unassigned players onto the smallest team.")
                    .define("autoAssignTeams", true);
            b.pop();

            b.push("territory");
            territoryUpdateInterval = b.comment("Ticks between territory influence recalculations.")
                    .defineInRange("updateIntervalTicks", 100, 20, 12000);
            territoryChunkRadius = b.comment("Maximum influence radius in chunks for the strongest sources.")
                    .defineInRange("maxRadiusChunks", 12, 1, 64);
            b.pop();

            b.push("intel");
            intelUpdateInterval = b.comment("Ticks between fog-of-war recalculations.")
                    .defineInRange("updateIntervalTicks", 40, 10, 1200);
            intelDecayTicks = b.comment("Ticks for observed terrain to decay one intel level.")
                    .defineInRange("decayTicks", 2400, 200, 200000);
            baseUnitVisionChunks = b.comment("Vision radius in chunks for a normal player/army.")
                    .defineInRange("armyVisionChunks", 4, 1, 32);
            scoutVisionChunks = b.comment("Vision radius in chunks for a scout.")
                    .defineInRange("scoutVisionChunks", 8, 1, 32);
            watchtowerVisionChunks = b.comment("Vision radius in chunks for a watchtower.")
                    .defineInRange("watchtowerVisionChunks", 10, 1, 48);
            nightVisionPenalty = b.comment("Multiplier applied to vision at night.")
                    .defineInRange("nightPenalty", 0.6D, 0.1D, 1.0D);
            stormVisionPenalty = b.comment("Multiplier applied to vision during rain/thunder.")
                    .defineInRange("stormPenalty", 0.7D, 0.1D, 1.0D);
            forestVisionPenalty = b.comment("Multiplier applied to vision in dense/forested biomes.")
                    .defineInRange("forestPenalty", 0.75D, 0.1D, 1.0D);
            b.pop();

            b.push("supply");
            supplyUpdateInterval = b.comment("Ticks between supply network recalculations.")
                    .defineInRange("updateIntervalTicks", 100, 20, 12000);
            supplySourceRadiusChunks = b.comment("Supply radius of a main base, in chunks.")
                    .defineInRange("sourceRadiusChunks", 10, 1, 64);
            supplyRelayRadiusChunks = b.comment("Supply radius of an outpost/depot, in chunks.")
                    .defineInRange("relayRadiusChunks", 6, 1, 64);
            unsuppliedSpeedPenalty = b.comment("Movement multiplier while unsupplied (1.0 = no penalty).")
                    .defineInRange("speedPenalty", 0.85D, 0.3D, 1.0D);
            unsuppliedDamagePenalty = b.comment("Outgoing damage multiplier while unsupplied.")
                    .defineInRange("damagePenalty", 0.9D, 0.3D, 1.0D);
            unsuppliedBlocksRegen = b.comment("Suppress natural regeneration while unsupplied.")
                    .define("blocksRegen", true);
            b.pop();

            b.push("villages");
            villageScanInterval = b.comment("Ticks between village discovery scans.")
                    .defineInRange("scanIntervalTicks", 600, 100, 24000);
            villageMaxTracked = b.comment("Hard cap on tracked villages, for performance.")
                    .defineInRange("maxTracked", 32, 1, 256);
            villageRequestIntervalTicks = b.comment("Average ticks between a village issuing a new request.")
                    .defineInRange("requestIntervalTicks", 6000, 600, 200000);
            villageRequestExpiryTicks = b.comment("Ticks before an unclaimed village request expires.")
                    .defineInRange("requestExpiryTicks", 12000, 600, 200000);
            villageReputationMax = b.comment("Absolute value of the reputation scale (+/-).")
                    .defineInRange("reputationMax", 100, 10, 10000);
            b.pop();

            b.push("relics");
            relicCount = b.comment("Number of relic sites generated per match.")
                    .defineInRange("count", 5, 0, 64);
            relicMinSpacingBlocks = b.comment("Minimum distance between two relic sites.")
                    .defineInRange("minSpacingBlocks", 400, 32, 10000);
            relicCaptureTicks = b.comment("Ticks a team must hold a relic site to capture it.")
                    .defineInRange("captureTicks", 1200, 100, 72000);
            relicSpawnRadiusBlocks = b.comment("Radius around world spawn in which relics are placed.")
                    .defineInRange("spawnRadiusBlocks", 1500, 100, 30000);
            relicGlobalDiscoveryAnnounce = b.comment("Announce relic discovery to all players, not just the discoverer.")
                    .define("globalDiscoveryAnnounce", false);
            b.pop();

            b.push("mercenaries");
            mercenaryCampCount = b.comment("Number of mercenary camps generated per match.")
                    .defineInRange("campCount", 6, 0, 64);
            mercenaryRespawnTicks = b.comment("Ticks before a cleared camp restocks.")
                    .defineInRange("respawnTicks", 9000, 600, 200000);
            b.pop();

            b.push("objectives");
            objectiveIntervalTicks = b.comment("Average ticks between new dynamic objectives.")
                    .defineInRange("intervalTicks", 4800, 600, 200000);
            objectiveMaxActive = b.comment("Maximum simultaneously active objectives.")
                    .defineInRange("maxActive", 3, 0, 16);
            b.pop();

            b.push("events");
            eventIntervalTicks = b.comment("Average ticks between world events at NORMAL frequency.")
                    .defineInRange("intervalTicks", 9000, 600, 400000);
            eventMinGapTicks = b.comment("Hard minimum gap between two events, to stop event spam.")
                    .defineInRange("minGapTicks", 3600, 100, 200000);
            eventMaxConcurrent = b.comment("Maximum concurrently running world events.")
                    .defineInRange("maxConcurrent", 2, 0, 8);
            b.pop();

            b.push("bosses");
            bossIntervalTicks = b.comment("Average ticks between world boss spawns.")
                    .defineInRange("intervalTicks", 24000, 1200, 400000);
            bossMaxActive = b.comment("Maximum simultaneously active world bosses.")
                    .defineInRange("maxActive", 1, 0, 8);
            bossHealthMultiplier = b.comment("Scales world boss health.")
                    .defineInRange("healthMultiplier", 1.0D, 0.1D, 20.0D);
            b.pop();

            b.push("spies");
            spyUpdateInterval = b.comment("Ticks between spy mission/detection updates.")
                    .defineInRange("updateIntervalTicks", 40, 10, 1200);
            spyDetectionChance = b.comment("Per-check chance a spy inside a detection radius is spotted.")
                    .defineInRange("detectionChance", 0.12D, 0.0D, 1.0D);
            spyDetectionRadius = b.comment("Detection radius in blocks around watchtowers and guards.")
                    .defineInRange("detectionRadius", 24, 4, 128);
            spyMissionDurationTicks = b.comment("Ticks a spy mission runs before it must report back.")
                    .defineInRange("missionDurationTicks", 3600, 200, 72000);
            b.pop();

            b.push("weather");
            weatherAffectsCombat = b.comment("Rain suppresses fire damage bonuses; storms raise lightning events.")
                    .define("affectsCombat", true);
            snowSlowFactor = b.comment("Movement multiplier for units in deep snow.")
                    .defineInRange("snowSlowFactor", 0.9D, 0.3D, 1.0D);
            b.pop();

            b.push("performance");
            onlyTickLoadedChunks = b.comment("Never force-load chunks for companion systems.")
                    .define("onlyTickLoadedChunks", true);
            maxStructureSearchRadius = b.comment("Maximum radius, in chunks, for any structure search.")
                    .defineInRange("maxStructureSearchChunks", 128, 8, 1024);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showEventFeed;
        public final ForgeConfigSpec.IntValue eventFeedLines;
        public final ForgeConfigSpec.IntValue eventFeedHoldTicks;
        public final ForgeConfigSpec.BooleanValue showToasts;
        public final ForgeConfigSpec.BooleanValue playEventSounds;
        public final ForgeConfigSpec.BooleanValue showWorldMarkers;
        public final ForgeConfigSpec.BooleanValue colorblindMode;
        public final ForgeConfigSpec.BooleanValue reducedAnimation;
        public final ForgeConfigSpec.DoubleValue fogIntensity;

        Client(ForgeConfigSpec.Builder b) {
            b.comment("Presentation and accessibility. These never affect gameplay.").push("hud");
            showEventFeed = b.define("showEventFeed", true);
            eventFeedLines = b.comment("How many event feed lines are visible at once.")
                    .defineInRange("eventFeedLines", 5, 0, 20);
            eventFeedHoldTicks = b.comment("How long a feed line stays on screen.")
                    .defineInRange("eventFeedHoldTicks", 200, 20, 6000);
            showToasts = b.define("showToasts", true);
            playEventSounds = b.define("playEventSounds", true);
            showWorldMarkers = b.define("showWorldMarkers", true);
            b.pop();

            b.comment("Accessibility (section 44).").push("accessibility");
            colorblindMode = b.comment("Always draw ownership glyphs alongside colour.")
                    .define("colorblindMode", false);
            reducedAnimation = b.comment("Disable pulsing/animated map and HUD effects.")
                    .define("reducedAnimation", false);
            fogIntensity = b.comment("How dark unexplored map areas are drawn.")
                    .defineInRange("fogIntensity", 0.8D, 0.0D, 1.0D);
            b.pop();
        }
    }
}
