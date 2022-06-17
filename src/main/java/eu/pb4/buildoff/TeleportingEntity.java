package eu.pb4.buildoff;

import eu.pb4.buildoff.mixin.VillagerEntityAccessor;
import eu.pb4.polymer.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.*;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.registry.Registry;
import net.minecraft.village.VillagerData;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

public class TeleportingEntity extends MobEntity implements PolymerEntity {
    public static EntityType<TeleportingEntity> TYPE = FabricEntityTypeBuilder.<TeleportingEntity>create(SpawnGroup.MISC, TeleportingEntity::new).dimensions(EntityDimensions.fixed(0.75f, 2f)).build();
    private final VillagerData villagerData;

    public TeleportingEntity(EntityType<TeleportingEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setCustomNameVisible(true);
        this.setSilent(true);
        this.setNoGravity(true);
        this.setCustomName(new LiteralText("Click to teleport to your plot!").formatted(Formatting.GOLD));
        this.villagerData = new VillagerData(Registry.VILLAGER_TYPE.getRandom(this.getRandom()).get().value(), Registry.VILLAGER_PROFESSION.getRandom(this.getRandom()).get().value(), 3);
    }

    public TeleportingEntity(World world) {
        this(TYPE, world);
    }

    public TeleportingEntity(World world, BuildArena arena) {
        this(world);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void tickMovement() {
        this.turnHead(this.getYaw(), this.getYaw());
    }

    @Override
    public boolean canTakeDamage() {
        return false;
    }

    @Override
    public boolean canBeLeashedBy(PlayerEntity player) {
        return false;
    }

    @Override
    public void attachLeash(Entity entity, boolean sendPacket) { }

    @Override
    public EntityType<?> getPolymerEntityType() {
        return EntityType.VILLAGER;
    }

    @Override
    public void modifyTrackedData(List<DataTracker.Entry<?>> data) {
        data.add(new DataTracker.Entry<>(VillagerEntityAccessor.get(), this.villagerData));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand == Hand.OFF_HAND) {
            return ActionResult.FAIL;
        }

        if (player instanceof ServerPlayerEntity serverPlayerEntity && serverPlayerEntity.server.getOverworld().getGameRules().getBoolean(BuildOffSetup.CAN_BUILD)) {
            var area = BuildOffSetup.mainGame.getArena(serverPlayerEntity);

            if (area == null) {
                area = BuildOffSetup.mainGame.createArena();
                area.addPlayer(serverPlayerEntity);
                area.updateHologram(BuildOffSetup.mainGame);
            }

            area.teleportPlayer(serverPlayerEntity, BuildOffSetup.world.asWorld());
        }

        return ActionResult.SUCCESS;
    }
}

