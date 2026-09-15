package com.netherfront;

/**
 * Brings up just enough of Minecraft for pure-logic tests.
 *
 * <p>Minecraft's registry classes will not initialise until the game bootstrap
 * has run, so any test touching a Minecraft type has to trigger it. Forge's
 * bootstrap then goes on to initialise its networking, which needs the
 * ModLauncher environment and cannot work in a plain JVM.
 *
 * <p>That later failure is harmless here: registries are set up before it, and
 * nothing under test needs Forge networking. So the failure is swallowed
 * deliberately, and the call is marked done either way so a second test class
 * does not retry and fail on the same step.
 */
public final class McBootstrap {
    private McBootstrap() {}

    private static boolean done;

    public static synchronized void ensure() {
        if (done) {
            return;
        }
        done = true;
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable t) {
            // Expected outside a ModLauncher environment; see the class note.
            System.out.println("[Netherfront tests] partial Minecraft bootstrap: " + t);
        }
    }
}
