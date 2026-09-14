package com.netherfront.client;

import com.netherfront.common.config.NFConfig;
import com.netherfront.common.feed.FeedCategory;
import com.netherfront.common.net.MatchSnapshot;
import com.netherfront.common.net.S2CNotifyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client-side mirror of the last snapshot the server sent.
 *
 * <p>Purely presentational: nothing here is authoritative and nothing here is
 * ever sent back as fact. If a value is missing the UI simply shows less.
 */
@OnlyIn(Dist.CLIENT)
public final class NFClientState {
    private NFClientState() {}

    private static MatchSnapshot snapshot = new MatchSnapshot();

    /** Notifications waiting to be drawn by the HUD, newest last. */
    private static final Deque<Notification> NOTIFICATIONS = new ArrayDeque<>();
    private static final int MAX_NOTIFICATIONS = 4;

    public record Notification(FeedCategory category, Component title, Component body,
                               @Nullable BlockPos position, long receivedAtMillis) {}

    public static MatchSnapshot snapshot() {
        return snapshot;
    }

    public static void acceptSnapshot(MatchSnapshot incoming) {
        snapshot = incoming;
    }

    public static void acceptNotification(S2CNotifyPacket packet) {
        Minecraft mc = Minecraft.getInstance();

        if (NFConfig.CLIENT.showToasts.get()) {
            NOTIFICATIONS.addLast(new Notification(packet.category(), packet.title(), packet.body(),
                    packet.position(), System.currentTimeMillis()));
            while (NOTIFICATIONS.size() > MAX_NOTIFICATIONS) {
                NOTIFICATIONS.removeFirst();
            }
        }

        if (packet.showTitle() && mc.gui != null) {
            mc.gui.setTimes(5, 40, 10);
            mc.gui.setTitle(packet.title().copy().withStyle(packet.category().color()));
            mc.gui.setSubtitle(packet.body());
        }

        if (packet.playSound() && NFConfig.CLIENT.playEventSounds.get() && mc.player != null) {
            SoundEvent sound = soundFor(packet.category());
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 0.6F));
        }
    }

    private static SoundEvent soundFor(FeedCategory category) {
        return switch (category) {
            case BOSS -> SoundEvents.WITHER_SPAWN;
            case RELIC -> SoundEvents.BEACON_ACTIVATE;
            case EVENT -> SoundEvents.RAID_HORN.get();
            case COMBAT -> SoundEvents.ANVIL_LAND;
            case DISCOVERY -> SoundEvents.PLAYER_LEVELUP;
            case ESPIONAGE -> SoundEvents.PHANTOM_FLAP;
            default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
        };
    }

    public static Deque<Notification> notifications() {
        return NOTIFICATIONS;
    }

    public static void clearNotifications() {
        NOTIFICATIONS.clear();
    }

    /** Drops notifications older than the configured hold time. */
    public static void expireNotifications() {
        long holdMillis = NFConfig.CLIENT.eventFeedHoldTicks.get() * 50L;
        long now = System.currentTimeMillis();
        NOTIFICATIONS.removeIf(n -> now - n.receivedAtMillis() > holdMillis);
    }

    public static void reset() {
        snapshot = new MatchSnapshot();
        NOTIFICATIONS.clear();
    }
}
