package com.netherfront.common.item;

import com.netherfront.common.agent.AgentRole;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.server.persistence.NFSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Turns a mob into a scout (section 4): fast, fragile, and with far better
 * vision than an army. Scouts trade survivability for reach, so losing one to
 * a defended position is the intended risk.
 */
public class ScoutOrdersItem extends Item {

    public ScoutOrdersItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return InteractionResult.PASS;
        }
        if (target instanceof Player) {
            return InteractionResult.PASS;
        }

        NFSavedData data = NFSavedData.get(serverPlayer.getServer());
        String team = data.match().teamIdOf(player.getUUID());
        if (MatchTeam.NEUTRAL.equals(team)) {
            serverPlayer.sendSystemMessage(Component.literal("You are not on a team.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        AgentRole existing = AgentRole.of(target);
        if (existing == AgentRole.SCOUT) {
            serverPlayer.sendSystemMessage(Component.literal("Already scouting.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }

        AgentRole.SCOUT.apply(target, team);
        // Fast and visible to its owner, but no tougher than it was.
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 20, 1, true, false));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 20, 0, true, false));

        target.level().playSound(null, target.blockPosition(),
                SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.NEUTRAL, 0.8F, 1.6F);
        serverPlayer.sendSystemMessage(Component.literal("Scout deployed. It sees further than your army.")
                .withStyle(ChatFormatting.AQUA));

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Use on a mob to make it a scout.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Wide vision, faster, no tougher.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
