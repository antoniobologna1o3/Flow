package com.netherfront.integration.reignofnether;

import com.netherfront.NetherfrontMod;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Soft integration with Reign of Nether (section 39).
 *
 * <p>Reign of Nether publishes no documented mod API, so this bridge does not
 * assume one. It resolves the few things it needs by reflection, once, at
 * startup, and disables itself cleanly if anything is missing. Netherfront's
 * own systems never depend on this returning a value: every call site has a
 * defined behaviour when the bridge is inactive.
 *
 * <p>Deliberately no compile-time dependency on Reign of Nether classes, so the
 * mod loads standalone and keeps working across RoN updates that move things.
 */
public final class RonBridge {
    private RonBridge() {}

    public static final String RON_MOD_ID = "reignofnether";

    private static boolean present;
    private static boolean reflectionReady;

    @Nullable
    private static Class<?> unitClass;
    @Nullable
    private static Method getOwnerNameMethod;

    public static void initialise() {
        present = ModList.get().isLoaded(RON_MOD_ID);
        if (!present) {
            NetherfrontMod.LOGGER.info("Reign of Nether not present; Netherfront runs standalone.");
            return;
        }
        resolveReflection();
    }

    private static void resolveReflection() {
        try {
            // Reign of Nether's units implement a common interface carrying the
            // owning player's name. If the name or shape has changed, we fall
            // back to Netherfront's own manual team assignment.
            unitClass = Class.forName("com.solegendary.reignofnether.unit.interfaces.Unit");
            for (Method method : unitClass.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                    String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                    if (name.contains("owner")) {
                        getOwnerNameMethod = method;
                        break;
                    }
                }
            }
            reflectionReady = getOwnerNameMethod != null;
            if (reflectionReady) {
                NetherfrontMod.LOGGER.info(
                        "Reign of Nether detected; unit ownership will be read via {}#{}",
                        unitClass.getSimpleName(), getOwnerNameMethod.getName());
            } else {
                NetherfrontMod.LOGGER.warn(
                        "Reign of Nether detected but its unit ownership accessor was not found. "
                        + "Netherfront will use manually assigned teams instead "
                        + "(/netherfront team assign).");
            }
        } catch (ClassNotFoundException e) {
            reflectionReady = false;
            NetherfrontMod.LOGGER.warn(
                    "Reign of Nether is loaded but its unit classes were not found at the expected "
                    + "location. Falling back to manual team assignment.");
        } catch (Exception e) {
            reflectionReady = false;
            NetherfrontMod.LOGGER.warn("Failed to wire Reign of Nether integration; using manual teams.", e);
        }
    }

    /** True when Reign of Nether is installed, regardless of reflection success. */
    public static boolean isAvailable() {
        return present;
    }

    /** True only when ownership can actually be read from RoN units. */
    public static boolean canResolveUnitOwners() {
        return present && reflectionReady;
    }

    /** True if this entity is a Reign of Nether unit. */
    public static boolean isRonUnit(Entity entity) {
        return present && unitClass != null && unitClass.isInstance(entity);
    }

    /**
     * Owning player name of a Reign of Nether unit.
     *
     * @return empty when RoN is absent, the entity is not a unit, or ownership
     *         could not be read. Callers treat empty as "neutral".
     */
    public static Optional<String> ownerNameOf(Entity entity) {
        if (!canResolveUnitOwners() || unitClass == null || getOwnerNameMethod == null) {
            return Optional.empty();
        }
        if (!unitClass.isInstance(entity)) {
            return Optional.empty();
        }
        try {
            Object result = getOwnerNameMethod.invoke(entity);
            if (result instanceof String s && !s.isBlank()) {
                return Optional.of(s);
            }
        } catch (Exception e) {
            // Log once at debug: this can happen per-entity and must not spam.
            NetherfrontMod.LOGGER.debug("Could not read Reign of Nether unit owner", e);
        }
        return Optional.empty();
    }
}
