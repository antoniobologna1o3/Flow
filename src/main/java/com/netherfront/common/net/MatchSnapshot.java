package com.netherfront.common.net;

import com.netherfront.common.feed.FeedEntry;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything one player is allowed to know, assembled server-side.
 *
 * <p>This is the only gameplay data that ever reaches a client. Because the
 * server filters it per team before sending, a modified client cannot reveal
 * anything it was not told (sections 3, 26 and 35).
 */
public final class MatchSnapshot {
    public String mode = "DYNAMIC_WAR";
    public String phase = "LOBBY";
    public String ownTeamId = "neutral";
    public int elapsedSeconds;
    public int dominationTarget;
    /** True when the viewing player's position is inside a supplied area. */
    public boolean supplied = true;
    /** Territory owner at the viewing player's position. */
    public String localTerritoryOwner = "neutral";

    public List<TeamView> teams = new ArrayList<>();
    public List<MapMarker> markers = new ArrayList<>();
    public List<ObjectiveView> objectives = new ArrayList<>();
    public List<EventView> events = new ArrayList<>();
    public List<FeedEntry> feed = new ArrayList<>();
    /** Fuzzy enemy assessments, never exact numbers (section 24). */
    public List<String> intelReports = new ArrayList<>();

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mode, 32);
        buf.writeUtf(phase, 32);
        buf.writeUtf(ownTeamId, 64);
        buf.writeInt(elapsedSeconds);
        buf.writeInt(dominationTarget);
        buf.writeBoolean(supplied);
        buf.writeUtf(localTerritoryOwner, 64);

        buf.writeVarInt(teams.size());
        teams.forEach(t -> t.encode(buf));
        buf.writeVarInt(markers.size());
        markers.forEach(m -> m.encode(buf));
        buf.writeVarInt(objectives.size());
        objectives.forEach(o -> o.encode(buf));
        buf.writeVarInt(events.size());
        events.forEach(e -> e.encode(buf));
        buf.writeVarInt(feed.size());
        feed.forEach(f -> f.encode(buf));
        buf.writeVarInt(intelReports.size());
        intelReports.forEach(r -> buf.writeUtf(r, 256));
    }

    public static MatchSnapshot decode(FriendlyByteBuf buf) {
        MatchSnapshot s = new MatchSnapshot();
        s.mode = buf.readUtf(32);
        s.phase = buf.readUtf(32);
        s.ownTeamId = buf.readUtf(64);
        s.elapsedSeconds = buf.readInt();
        s.dominationTarget = buf.readInt();
        s.supplied = buf.readBoolean();
        s.localTerritoryOwner = buf.readUtf(64);

        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) s.teams.add(TeamView.decode(buf));
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) s.markers.add(MapMarker.decode(buf));
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) s.objectives.add(ObjectiveView.decode(buf));
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) s.events.add(EventView.decode(buf));
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) s.feed.add(FeedEntry.decode(buf));
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) s.intelReports.add(buf.readUtf(256));
        return s;
    }
}
