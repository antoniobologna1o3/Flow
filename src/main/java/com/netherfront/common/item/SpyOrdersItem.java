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
 * Sends a mob on a spy mission (section 15).
 *
 * <p>Spies exist to gather information, not to win fights: they are given
 * invisibility and nothing else, and any watchtower or guard can expose them.
 * Sabotage is deliberately limited so espionage never replaces actually
 * fighting the battle in Reign of Nether.
 */
public class SpyOrdersItem extends Item {

    public SpyOrdersItem(Properties properties) {
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
        if (AgentRole.is(target, AgentRole.SPY)) {
            serverPlayer.sendSystemMessage(Component.literal("Already on a mission.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }

        AgentRole.SPY.apply(target, team);
        target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 60 * 10, 0, true, false));

        target.level().playSound(null, target.blockPosition(),
                SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.5F, 1.4F);
        serverPlayer.sendSystemMessage(
                Component.literal("Spy dispatched. Keep it away from watchtowers.")
                        .withStyle(ChatFormatting.DARK_PURPLE));

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Use on a mob to send it spying.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Reveals enemy territory. Can be detected.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
