package com.netherfront.common.match;

/** Lifecycle of a Netherfront match. */
public enum MatchPhase {
    /** Teams are being configured; no companion system ticks yet. */
    LOBBY,
    /** Systems are live. */
    ACTIVE,
    /** Match ended; statistics are frozen and the report is available. */
    ENDED
}
