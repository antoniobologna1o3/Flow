package com.netherfront.common.net;

import com.netherfront.common.MatchContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * Implemented by any subsystem that has something to show a player.
 *
 * <p>The contributor is handed the viewing player and their team and decides
 * what that team is allowed to see. This is the one place fog-of-war filtering
 * happens, and it happens on the server, so adding a new system cannot
 * accidentally leak information to clients.
 */
public interface SnapshotContributor {
    void contribute(MatchContext ctx, ServerPlayer viewer, String viewerTeam, MatchSnapshot snapshot);
}
