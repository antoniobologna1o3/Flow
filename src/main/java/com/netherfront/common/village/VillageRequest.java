package com.netherfront.common.village;

import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.util.NbtUtils2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One outstanding village request. Requests are open to every team until one
 * claims it by making progress, which turns village favour into something the
 * two sides can race for.
 */
public final class VillageRequest {

    private final UUID id;
    private final VillageRequestType type;
    private final int required;
    private int progress;
    private final long expiresAtTick;
    private String claimedByTeam = MatchTeam.NEUTRAL;
    @Nullable
    private final BlockPos targetPos;
    /** For SUPPLY_RESOURCE only. */
    private final Item requestedItem;

    public VillageRequest(UUID id, VillageRequestType type, int required, long expiresAtTick,
                          @Nullable BlockPos targetPos, Item requestedItem) {
        this.id = id;
        this.type = type;
        this.required = Math.max(1, required);
        this.expiresAtTick = expiresAtTick;
        this.targetPos = targetPos == null ? null : targetPos.immutable();
        this.requestedItem = requestedItem;
    }

    public UUID id() {
        return id;
    }

    public VillageRequestType type() {
        return type;
    }

    public int required() {
        return required;
    }

    public int progress() {
        return progress;
    }

    public long expiresAtTick() {
        return expiresAtTick;
    }

    public String claimedByTeam() {
        return claimedByTeam;
    }

    @Nullable
    public BlockPos targetPos() {
        return targetPos;
    }

    public Item requestedItem() {
        return requestedItem;
    }

    public boolean isComplete() {
        return progress >= required;
    }

    public boolean isExpired(long now) {
        return now >= expiresAtTick;
    }

    public float progressFraction() {
        return Math.min(1.0F, (float) progress / required);
    }

    /**
     * Adds progress on behalf of a team.
     *
     * <p>The first team to contribute claims the request; further progress from
     * another team is ignored, so two teams cannot both be paid for one job.
     *
     * @return true if this call completed the request
     */
    public boolean addProgress(String teamId, int amount) {
        if (MatchTeam.NEUTRAL.equals(teamId) || amount <= 0 || isComplete()) {
            return false;
        }
        if (MatchTeam.NEUTRAL.equals(claimedByTeam)) {
            claimedByTeam = teamId;
        } else if (!claimedByTeam.equals(teamId)) {
            return false;
        }
        progress = Math.min(required, progress + amount);
        return isComplete();
    }

    public String describe() {
        return switch (type) {
            case CLEAR_MOBS -> String.format(type.template(), required);
            case SUPPLY_RESOURCE -> String.format(type.template(), required,
                    requestedItem.getDescription().getString());
            case DEFEND_VILLAGE -> String.format(type.template(), (required / 20) + "s");
            default -> type.template();
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", type.name());
        tag.putInt("required", required);
        tag.putInt("progress", progress);
        tag.putLong("expires", expiresAtTick);
        tag.putString("claimed", claimedByTeam);
        if (targetPos != null) {
            NbtUtils2.putBlockPos(tag, "target", targetPos);
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(requestedItem);
        tag.putString("item", itemId == null ? "minecraft:air" : itemId.toString());
        return tag;
    }

    @Nullable
    public static VillageRequest load(CompoundTag tag) {
        VillageRequestType type;
        try {
            type = VillageRequestType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Item item = Items.AIR;
        ResourceLocation itemId = ResourceLocation.tryParse(tag.getString("item"));
        if (itemId != null) {
            Item found = ForgeRegistries.ITEMS.getValue(itemId);
            if (found != null) {
                item = found;
            }
        }
        VillageRequest request = new VillageRequest(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                type,
                tag.getInt("required"),
                tag.getLong("expires"),
                tag.contains("target") ? NbtUtils2.getBlockPos(tag, "target") : null,
                item);
        request.progress = tag.getInt("progress");
        request.claimedByTeam = tag.getString("claimed");
        if (request.claimedByTeam.isEmpty()) {
            request.claimedByTeam = MatchTeam.NEUTRAL;
        }
        return request;
    }
}
