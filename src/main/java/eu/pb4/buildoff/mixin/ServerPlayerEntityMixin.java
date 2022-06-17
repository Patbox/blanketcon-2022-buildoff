package eu.pb4.buildoff.mixin;

import com.mojang.authlib.GameProfile;
import eu.pb4.buildoff.BuildOffSetup;
import eu.pb4.buildoff.PlayerManagerAccess;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity {
    @Shadow @Final public MinecraftServer server;

    @Shadow public abstract boolean changeGameMode(GameMode gameMode);

    @Shadow public abstract void setCameraEntity(@Nullable Entity entity);

    @Shadow public abstract PlayerAdvancementTracker getAdvancementTracker();

    @Shadow public abstract void setWorld(ServerWorld world);

    @Shadow public ServerPlayNetworkHandler networkHandler;

    @Shadow @Final public ServerPlayerInteractionManager interactionManager;

    public ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void onTeleport(ServerWorld targetWorld, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (this.world != targetWorld) {
            handleTeleport(targetWorld);
        }
    }

    @Inject(method = "moveToWorld", at = @At("HEAD"), cancellable = true)
    private void onMoveWorld(ServerWorld targetWorld, CallbackInfoReturnable<Entity> ci) {
        if (this.world != targetWorld) {
            handleTeleport(targetWorld);
        }
    }

    @Unique
    private void handleTeleport(ServerWorld targetWorld) {
        boolean cleanup = false;

        if (targetWorld == BuildOffSetup.world.asWorld()) {
            ((PlayerManagerAccess) this.server.getPlayerManager()).buildoff$savePlayerData((ServerPlayerEntity) (Object) this);
            BuildOffSetup.mainGame.currentlyIn.add(this.getUuid());
            ((PlayerManagerAccess) this.server.getPlayerManager()).buildoff$getPlayerResetter().apply((ServerPlayerEntity) (Object) this);

            this.changeGameMode(GameMode.CREATIVE);
            cleanup = true;
        } else if (this.world == BuildOffSetup.world.asWorld()) {
            ((PlayerManagerAccess) this.server.getPlayerManager()).buildoff$getPlayerResetter().apply((ServerPlayerEntity) (Object) this);
            ((PlayerManagerAccess) this.server.getPlayerManager()).buildoff$loadIntoPlayer((ServerPlayerEntity) (Object) this);
            this.changeGameMode(GameMode.ADVENTURE);
            BuildOffSetup.mainGame.currentlyIn.remove(uuid);
            cleanup = true;
        }

        if (cleanup) {
            var playerManager = this.server.getPlayerManager();
            var playerManagerAccess = (PlayerManagerAccess) playerManager;
            var player = (ServerPlayerEntity) (Object) this;

            this.detach();
            this.setCameraEntity(this);

            this.getAdvancementTracker().clearCriteria();
            this.server.getBossBarManager().onPlayerDisconnect((ServerPlayerEntity) (Object) this);

            ((ServerWorld) this.getWorld()).removePlayer((ServerPlayerEntity) (Object) this, Entity.RemovalReason.CHANGED_DIMENSION);
            this.unsetRemoved();

            this.setWorld(targetWorld);

            var worldProperties = world.getLevelProperties();

            var networkHandler = this.networkHandler;
            networkHandler.sendPacket(new PlayerRespawnS2CPacket(
                    targetWorld.method_40134(),targetWorld.getRegistryKey(),
                    BiomeAccess.hashSeed(targetWorld.getSeed()),
                    this.interactionManager.getGameMode(), this.interactionManager.getPreviousGameMode(),
                    targetWorld.isDebugWorld(), targetWorld.isFlat(), false
            ));

            networkHandler.sendPacket(new DifficultyS2CPacket(worldProperties.getDifficulty(), worldProperties.isDifficultyLocked()));
            networkHandler.sendPacket(new PlayerAbilitiesS2CPacket(this.getAbilities()));
            networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(this.getInventory().selectedSlot));

            this.closeHandledScreen();

            playerManager.sendCommandTree((ServerPlayerEntity) (Object) this);

            targetWorld.onPlayerTeleport(player);
            networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());

            this.server.getBossBarManager().onPlayerConnect(player);

            playerManager.sendWorldInfo(player, targetWorld);
            playerManager.sendPlayerStatus(player);


            for (var effect : player.getStatusEffects()) {
                networkHandler.sendPacket(new EntityStatusEffectS2CPacket(player.getId(), effect));
            }
        }
    }
}