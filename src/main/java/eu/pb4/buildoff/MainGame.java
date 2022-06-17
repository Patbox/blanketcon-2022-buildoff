package eu.pb4.buildoff;


import eu.pb4.buildoff.items.WrappedItem;
import eu.pb4.holograms.api.Holograms;
import eu.pb4.holograms.api.holograms.AbstractHologram;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.IdList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.GameMode;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.stimuli.event.EventListenerMap;
import xyz.nucleoid.stimuli.event.block.BlockBreakEvent;
import xyz.nucleoid.stimuli.event.block.BlockPlaceEvent;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;
import xyz.nucleoid.stimuli.event.entity.EntitySpawnEvent;
import xyz.nucleoid.stimuli.event.item.ItemUseEvent;
import xyz.nucleoid.stimuli.event.player.PlayerAttackEntityEvent;
import xyz.nucleoid.stimuli.event.player.PlayerC2SPacketEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.player.PlayerSwingHandEvent;
import xyz.nucleoid.stimuli.event.world.ExplosionDetonatedEvent;
import xyz.nucleoid.stimuli.event.world.FluidFlowEvent;

import java.util.*;

public class MainGame {
    private final static BlockBounds baseBounds = BlockBounds.of(9, 1,55, 55, 128, 9);
    private final static BlockBounds baseSpawn = BlockBounds.ofBlock(new BlockPos(31, 33, 57));
    public final IdList<AbstractHologram> holograms = new IdList<>();
    public final ServerWorld world;

    public int currentTick = 0;
    public List<BuildArena> arenas = new ArrayList<>();

    public Set<UUID> currentlyIn = new HashSet<>();

    public int currentArena = 0;
    private final boolean lockBuilding = false;
    public MinecraftServer server;

    public MainGame(MinecraftServer server, ServerWorld world, EventListenerMap events) {
        this.world = world;
        this.server = server;

        var data = StorageData.loadOrCreateConfig();

        this.currentArena = data.current;
        this.arenas.addAll(data.arenas);

        for (var arena : this.arenas) {
            arena.updateHologram(this);
        }

        events.listen(BlockPlaceEvent.BEFORE, this::onPlaceBlock);
        events.listen(BlockBreakEvent.EVENT, this::onBreakBlock);
        events.listen(ItemUseEvent.EVENT, this::onItemUse);
        events.listen(BlockUseEvent.EVENT, this::onBlockUse);
        events.listen(BuildOffSetup.ON_BUCKET_USAGE, this::onFluidPlace);

        events.listen(ItemUseEvent.EVENT, this::onItemUse);

        events.listen(PlayerAttackEntityEvent.EVENT, this::onEntityDamage);
        events.listen(ExplosionDetonatedEvent.EVENT, this::onExplosion);
        events.listen(FluidFlowEvent.EVENT, this::onFluidFlow);

        events.listen(PlayerC2SPacketEvent.EVENT, this::onClientPacket);

        events.listen(PlayerDeathEvent.EVENT, this::onPlayerDeath);
        events.listen(PlayerSwingHandEvent.EVENT, this::onPlayerSwing);

        events.listen(EntitySpawnEvent.EVENT, this::onEntitySpawn);

    }

    @Nullable
    public BuildArena getArena(BlockPos pos) {
        for (var arena : this.arenas) {
            if (arena.buildingArea.contains(pos)) {
                return arena;
            }
        }
        return null;
    }

    @Nullable
    public BuildArena getArena(ServerPlayerEntity player) {
        for (var arena : this.arenas) {
            if (arena.isBuilder(player)) {
                return arena;
            }
        }
        return null;
    }

    public BuildArena createArena() {
        var arena = new BuildArena(this.currentArena, baseBounds.offset(this.currentArena * 64, 0, 0), baseSpawn.offset(this.currentArena * 64, 0, 0));
        this.arenas.add(arena);
        this.currentArena++;
        return arena;
    }
    
    public boolean canBuild(BlockPos pos, ServerPlayerEntity player) {
        if (player.getOffHandStack().isOf(BuildOffSetup.WRAPPED)
                && player.getOffHandStack().getOrCreateNbt().getString("wrappedId").equals("minecraft:stick")
                && player.getCommandSource().hasPermissionLevel(3)
        ) {
            return true;
        }

        var arena = this.getArena(pos);

        if (player.server.getOverworld().getGameRules().getBoolean(BuildOffSetup.CAN_BUILD) && arena != null && arena.canBuild(pos, player)) {
            return true;
        }

        return false;
    } 

    private ActionResult onFluidFlow(ServerWorld world, BlockPos blockPos, BlockState state, Direction direction, BlockPos blockPos1, BlockState state1) {
        var arena = this.getArena(blockPos1);

        if (arena != null && arena.buildingArea.contains(blockPos1)) {
            return ActionResult.PASS;
        }

        return ActionResult.FAIL;
    }

    private ActionResult onEntitySpawn(Entity entity) {
        if (entity instanceof ServerPlayerEntity) {
            return ActionResult.PASS;
        } else if (BbUtils.equalsOrInstance(entity, ItemEntity.class)) {
            return ActionResult.FAIL;
        } else {
            var arena = this.getArena(entity.getBlockPos());

            if (arena != null) {
                if (entity.getEntityWorld().getOtherEntities(null, arena.buildingArea.asBox(), (e) -> !(e instanceof PlayerEntity)).size() > 32) {
                    return ActionResult.FAIL;
                }
            }
        }


        return ActionResult.PASS;
    }

    private ActionResult onClientPacket(ServerPlayerEntity player, Packet<?> packet) {
        if (packet instanceof BookUpdateC2SPacket) {
            return ActionResult.FAIL;
        }

        if (packet instanceof CreativeInventoryActionC2SPacket packet1) {
            ItemStack stack = packet1.getItemStack();

            if (!Registry.ITEM.getId(stack.getItem()).getNamespace().equals("buildoff")) {
                if (stack.getItem().getGroup() == null || stack.getItem().getClass().getPackageName().contains("com.simibubi.create.content.curiosities")) {
                    stack = ItemStack.EMPTY;
                } else {
                    if (stack.getItem() instanceof BlockItem || (stack.getItem() instanceof BucketItem && !(stack.getItem() instanceof EntityBucketItem))) {
                        if (stack.hasNbt()) {
                            NbtCompound nbt = new NbtCompound();
                            NbtCompound og = stack.getNbt();

                            assert og != null;
                            if (og.contains("Patterns", NbtElement.LIST_TYPE)) {
                                NbtList list = og.getList("Patterns", NbtElement.COMPOUND_TYPE);
                                if (list.size() <= 6) {
                                    nbt.put("Patterns", list);
                                }
                            }

                            stack.setNbt(nbt);
                        }
                    } else {
                        stack = WrappedItem.createWrapped(stack);
                    }
                }
            }

            BbUtils.setCreativeStack(packet1, stack);
            player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(player.playerScreenHandler.syncId, 0, packet1.getSlot(), stack));
        }

        return ActionResult.PASS;
    }


    private ActionResult onEntityDamage(ServerPlayerEntity player, Hand hand, Entity entity, EntityHitResult entityHitResult) {
        BuildArena arena = this.getArena(entity.getBlockPos());

        if (arena != null && arena.isBuilder(player)) {
            return ActionResult.PASS;
        }

        return ActionResult.FAIL;
    }

    private void onExplosion(Explosion explosion, boolean particles) {
        explosion.clearAffectedBlocks();
    }

    private TypedActionResult<ItemStack> onItemUse(ServerPlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        Item item = stack.getItem();
        if (BbUtils.equalsOrInstance(item, Items.CHORUS_FRUIT, Items.ENDER_PEARL, Items.ENDER_EYE)) {
            return TypedActionResult.fail(stack);
        }

        return TypedActionResult.pass(stack);
    }

    private ActionResult onBlockUse(ServerPlayerEntity player, Hand hand, BlockHitResult hitResult) {

        if (!(this.canBuild(hitResult.getBlockPos(), player) || this.canBuild(hitResult.getBlockPos().offset(hitResult.getSide()), player))) {
            return ActionResult.FAIL;
        }

        ItemStack stack = player.getStackInHand(hand);
        var item = stack.getItem();
        if (BbUtils.equalsOrInstance(item, Items.ARMOR_STAND, EntityBucketItem.class, SpawnEggItem.class, BoatItem.class)) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private ActionResult onBreakBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (this.lockBuilding) {
            return ActionResult.FAIL;
        }

        if (this.canBuild(pos, player)) {
            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }


    private void onPlayerSwing(ServerPlayerEntity player, Hand hand) {
        if (Thread.currentThread() != player.server.getThread()) {
            return;
        }

    }

    private ActionResult onPlaceBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state, ItemUsageContext itemUsageContext) {
        if (this.lockBuilding) {
            return ActionResult.FAIL;
        }

        if (this.canBuild(pos, player)) {
            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }

    private ActionResult onFluidPlace(ServerPlayerEntity player, BlockPos blockPos) {
        if (this.lockBuilding) {
            return ActionResult.FAIL;
        }

        if (this.canBuild(blockPos, player)) {
            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnParticipant(player);

        return ActionResult.FAIL;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.setHealth(20);
        player.changeGameMode(GameMode.CREATIVE);
        //player.getInventory().offerOrDrop(WrappedItem.createWrapped("test"));
        var arena = this.getArena(player);

        if (arena != null) {
            arena.teleportPlayer(player, this.world);
        } else {
            player.teleport(5,33,5, false);
        }
    }

    public void tick() {
        int time = this.currentTick;
        this.currentTick++;

        if (time % (20 * 60 * 10) == 0) {
           this.save();
        }

        /*
        if (time >= this.buildingTimeDuration) {
            this.gameSpace.getPlayers().sendMessage(FormattingUtil.format(FormattingUtil.HOURGLASS_PREFIX, new TranslatableText("text.buildbattle.build_time_ended").formatted(Formatting.GREEN)));
            this.lockBuilding = true;
            this.timerBar.setColor(BossBar.Color.RED);
            this.timerBar.update(new TranslatableText("text.buildbattle.timer_bar.times_up"), 0);
            for (BuildArena buildArena : this.buildArena) {
                buildArena.removeEntity();
            }
            this.phase = Phase.WAITING;
            this.gameSpace.getPlayers().playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);

        } else {
            int ticksLeft = this.buildingTimeDuration - time;

            int secondsUntilEnd = ticksLeft / 20 + 1;

            int minutes = secondsUntilEnd / 60;
            int seconds = secondsUntilEnd % 60;
            if (minutes == 0 && seconds < 10 && time % 20 == 0) {
                this.gameSpace.getPlayers().playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }

            this.timerBar.setColor(BossBar.Color.GREEN);
            this.timerBar.update(new TranslatableText("text.buildbattle.timer_bar.time_left", String.format("%02d:%02d", minutes, seconds))
                    .append(new LiteralText(" - ").formatted(Formatting.GRAY))
                    .append(new TranslatableText("text.buildbattle.timer_bar.theme").formatted(Formatting.YELLOW))
                    .append(new LiteralText(theme)), ((float) ticksLeft) / (this.buildingTimeDuration - this.themeVotingTime));

            if (time % 10 == 0) {
                var borderEffect = new DustParticleEffect(new Vec3f(0.8f, 0.8f, 0.8f), 2.0F);
                var selectionEffect = new DustParticleEffect(new Vec3f(0.8f, 0.3f, 0.3f), 1.8F);
                for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
                    var data = this.participants.get(PlayerRef.of(player));
                    if (data != null) {
                        ParticleOutlineRenderer.render(player, data.arena.buildingArea.min(), data.arena.buildingArea.max().add(1, 1, 1), borderEffect);

                        if (data.isSelected()) {
                            ParticleOutlineRenderer.render(player, BlockBounds.min(data.selectionStart, data.selectionEnd), BlockBounds.max(data.selectionStart, data.selectionEnd).add(1, 1, 1), selectionEffect);
                        }
                    }
                }

                ParticleEffect effect2 = new DustParticleEffect(new Vec3f(0f, 1f, 0f), 2.0F);
                for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
                    PlayerData data = this.participants.get(PlayerRef.of(player));
                    if (data != null) {
                        ParticleOutlineRenderer.render(player, data.arena.bounds.min(), data.arena.bounds.max().add(1, 1, 1), effect2);
                    }
                }
            }
        }*/
    }

    public void save() {
        var data = new StorageData();
        data.current = this.currentArena;
        data.arenas.addAll(this.arenas);
        StorageData.saveConfig(data);
    }

}
