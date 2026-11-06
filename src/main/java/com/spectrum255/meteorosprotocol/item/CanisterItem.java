package com.spectrum255.meteorosprotocol.item;

import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.MixedStorage;
import com.spectrum255.meteorosprotocol.particle.ParticleCapabilities;
import com.spectrum255.meteorosprotocol.particle.ParticleNbt;
import com.spectrum255.meteorosprotocol.particle.Phase;
import com.spectrum255.meteorosprotocol.particle.PhaseStorage;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A 1,000 PU particle canister. Contents live in the vanilla custom-data
 * component as {@code {Particles:{unit:"mPU", <phase>:<long>...}}} so the
 * same payload can be moved between machines and items.
 *
 * <p>Amounts are indexed by {@link Phase#index()}. New saves use the full
 * registry id as the key; saves from before the phase registry used the bare
 * path, which {@link ParticleNbt#readAmount} still accepts.</p>
 */
public class CanisterItem extends Item {
    public static final long CAPACITY = 1_000_000L;

    public CanisterItem(Properties properties) {
        super(properties);
    }

    /** Per-phase mPU, in {@link Phase#all()} order. */
    public static long[] getAmounts(ItemStack stack) {
        long[] amounts = new long[Phase.all().size()];
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return amounts;
        }
        CompoundTag particles = customData.copyTag().getCompound("Particles");
        boolean legacyPu = !"mPU".equals(particles.getString("unit"));
        for (Phase phase : Phase.all()) {
            amounts[phase.index()] = ParticleNbt.readAmount(particles, phase, legacyPu);
        }
        return amounts;
    }

    public static long getTotal(ItemStack stack) {
        long total = 0L;
        for (long amount : getAmounts(stack)) {
            total += amount;
        }
        return total;
    }

    /** Writes phase amounts, clamping each phase so the total cannot exceed capacity. */
    public static void setAmounts(ItemStack stack, long[] amounts) {
        CompoundTag particles = new CompoundTag();
        particles.putString("unit", "mPU");
        long remaining = CAPACITY;
        for (Phase phase : Phase.all()) {
            long amount = Math.max(0L, Math.min(amounts[phase.index()], remaining));
            particles.putLong(phase.id(), amount);
            remaining -= amount;
        }
        CompoundTag root = new CompoundTag();
        root.put("Particles", particles);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
    }

    /**
     * Right-clicking a machine with a canister moves particles instead of opening
     * its menu: a loaded canister fills the machine's mixed buffer, an empty one
     * drains the phase tanks first and the mixed buffer afterwards. Sneaking keeps
     * the old behaviour and opens the machine menu.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        MachineBlockEntity machine = level.getBlockEntity(pos) instanceof MachineBlockEntity found ? found : null;
        if (machine == null) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        // In 1.21.1 a sneak right-click skips useWithoutItem entirely, so the item
        // has to open the menu itself to keep "sneak = GUI" predictable.
        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(machine);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // The server is authoritative; the client only confirms the prediction so
        // the hand swing looks right.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        MixedStorage storage = level.getCapability(
                ParticleCapabilities.MIXED_STORAGE,
                pos,
                context.getClickedFace()
        );
        // Side views restrict insert/extract; a handheld canister is meant to work
        // from any face, so fall back to the block entity like the creative canister.
        if (!(storage instanceof MachineBlockEntity)) {
            storage = machine;
        }

        long[] amounts = getAmounts(stack);
        long stored = 0L;
        for (long amount : amounts) {
            stored += amount;
        }

        // Rule: deposit when the machine can take the canister's contents, otherwise
        // top the canister up from the machine. This also covers half-full canisters.
        if (stored > 0L && canAccept(storage, amounts)) {
            long moved = deposit(storage, amounts);
            if (moved > 0L) {
                setAmounts(stack, amounts);
                feedback(player, level, pos, "message.meteorosprotocol.canister.filled", moved, SoundEvents.BOTTLE_FILL);
                return InteractionResult.SUCCESS;
            }
        }

        if (stored < CAPACITY && machineHasParticles(machine)) {
            long[] taken = new long[Phase.all().size()];
            long moved = withdraw(machine, storage, taken);
            if (moved > 0L) {
                setAmounts(stack, taken);
                feedback(player, level, pos, "message.meteorosprotocol.canister.emptied", moved, SoundEvents.BOTTLE_EMPTY);
                return InteractionResult.SUCCESS;
            }
        }

        // Nothing moved: say why instead of swallowing the click.
        notify(player, stored >= CAPACITY
                ? "message.meteorosprotocol.canister.full"
                : "message.meteorosprotocol.canister.empty");
        return InteractionResult.SUCCESS;
    }

    /** True when at least one phase the canister carries would be accepted right now. */
    private static boolean canAccept(MixedStorage storage, long[] amounts) {
        for (Phase phase : Phase.all()) {
            long have = amounts[phase.index()];
            if (have > 0L && storage.insert(phase, have, true) > 0L) {
                return true;
            }
        }
        return false;
    }

    /** True when the machine's phase tanks or mixed buffer hold anything at all. */
    private static boolean machineHasParticles(MachineBlockEntity machine) {
        for (Phase phase : Phase.all()) {
            if (machine.getAmount(phase) > 0L || machine.phaseStorage().getAmount(phase) > 0L) {
                return true;
            }
        }
        return false;
    }

    /** Deposits every locked-in phase into the machine; amounts[] receives the leftovers. */
    private static long deposit(MixedStorage storage, long[] amounts) {
        long moved = 0L;
        for (Phase phase : Phase.all()) {
            long have = amounts[phase.index()];
            if (have <= 0L) {
                continue;
            }
            long accepted = storage.insert(phase, have, false);
            if (accepted > 0L) {
                amounts[phase.index()] -= accepted;
                moved += accepted;
            }
        }
        return moved;
    }

    /**
     * Fills a canister from a machine: phase tanks first (richest phase first),
     * then the mixed buffer. Returns the moved amount and writes it into taken[].
     */
    private static long withdraw(MachineBlockEntity machine, MixedStorage mixed, long[] taken) {
        long space = CAPACITY;
        PhaseStorage tanks = machine.phaseStorage();
        while (space > 0L) {
            Phase best = null;
            long bestAmount = 0L;
            for (Phase phase : Phase.all()) {
                long amount = tanks.getAmount(phase);
                if (amount > bestAmount) {
                    bestAmount = amount;
                    best = phase;
                }
            }
            if (best == null) {
                break;
            }
            long got = tanks.extract(best, space, false);
            if (got <= 0L) {
                break;
            }
            taken[best.index()] += got;
            space -= got;
        }
        if (space > 0L) {
            for (Phase phase : Phase.all()) {
                if (space <= 0L) {
                    break;
                }
                long got = mixed.extract(phase, space, false);
                if (got <= 0L) {
                    continue;
                }
                taken[phase.index()] += got;
                space -= got;
            }
        }
        return CAPACITY - space;
    }

    private static void feedback(Player player, Level level, BlockPos pos, String messageKey, long amount, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.6F, 1.0F);
        if (player != null) {
            player.displayClientMessage(Component.translatable(messageKey, amount), true);
        }
    }

    /** Action bar note without a value; used when a right-click cannot move anything. */
    private static void notify(Player player, String messageKey) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getTotal(stack) > 0L;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long total = getTotal(stack);
        return (int) Math.max(0L, Math.min(13L, Math.round(13.0 * total / CAPACITY)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        long[] amounts = getAmounts(stack);
        List<Phase> phases = Phase.all();
        int best = -1;
        long bestAmount = 0L;
        int limit = Math.min(phases.size(), amounts.length);
        for (int index = 0; index < limit; index++) {
            if (amounts[index] > bestAmount) {
                bestAmount = amounts[index];
                best = index;
            }
        }
        return best < 0 ? 0xFFFFFFFF : phases.get(best).argb();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long total = getTotal(stack);
        tooltip.add(Component.translatable("tooltip.meteorosprotocol.canister.total", total, CAPACITY)
                .withStyle(ChatFormatting.GRAY));
    }
}
