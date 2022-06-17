package eu.pb4.buildoff.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import eu.pb4.buildoff.BuildOffSetup;
import eu.pb4.buildoff.PlayerManagerAccess;
import eu.pb4.buildoff.PlayerResetter;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.WorldSaveHandler;
import net.minecraft.world.dimension.DimensionType;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin implements PlayerManagerAccess {
    @Shadow
    @Final
    private MinecraftServer server;
    @Shadow
    @Final
    private WorldSaveHandler saveHandler;
    @Shadow
    @Final
    private Map<UUID, ServerStatHandler> statisticsMap;
    @Shadow
    @Final
    private Map<UUID, PlayerAdvancementTracker> advancementTrackers;
    @Unique
    private PlayerResetter playerResetter;

    @Shadow
    protected abstract void savePlayerData(ServerPlayerEntity player);

    @Shadow
    public abstract NbtCompound getUserData();

    @Shadow @Final private static Logger LOGGER;

    @Inject(method = "remove", at = @At("TAIL"))
    private void removeerf(ServerPlayerEntity player, CallbackInfo ci) {
        BuildOffSetup.mainGame.currentlyIn.remove(player.getUuid());
    }

    @Inject(
            method = "respawnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;copyFrom(Lnet/minecraft/server/network/ServerPlayerEntity;Z)V",
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void respawnPlayer(
            ServerPlayerEntity oldPlayer, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> ci,
            BlockPos spawnPos, float spawnAngle, boolean spawnSet, ServerWorld spawnWorld, Optional<Vec3d> respawnPoint,
            ServerWorld respawnWorld, ServerPlayerEntity respawnedPlayer
    ) {

        if (BuildOffSetup.mainGame.currentlyIn.contains(oldPlayer.getUuid())) {
            respawnedPlayer.setWorld(respawnWorld);
            this.buildoff$loadIntoPlayer(respawnedPlayer);
            respawnedPlayer.setWorld(respawnWorld);

            // this is later used to apply back to the respawned player, and we want to maintain that
            var interactionManager = respawnedPlayer.interactionManager;
            oldPlayer.interactionManager.changeGameMode(interactionManager.getGameMode());
        }
    }

    @Override
    public void buildoff$savePlayerData(ServerPlayerEntity player) {
        this.savePlayerData(player);
    }

    @Override
    public void buildoff$loadIntoPlayer(ServerPlayerEntity player) {
        var userData = this.getUserData();
        if (userData == null) {
            userData = this.server.getSaveProperties().getPlayerData();
        }

        NbtCompound playerData;
        if (this.server.isHost(player.getGameProfile()) && userData != null) {
            playerData = userData;
            player.readNbt(userData);
        } else {
            playerData = this.saveHandler.loadPlayerData(player);
        }

        var dimension = playerData != null ? this.getDimensionFromData(playerData) : null;

        var world = this.server.getWorld(dimension);
        if (world == null) {
            world = this.server.getOverworld();
        }

        player.setWorld(world);
        player.interactionManager.setWorld(world);
        BuildOffSetup.mainGame.currentlyIn.remove(player.getUuid());
        player.setGameMode(playerData);
    }

    @Unique
    private RegistryKey<World> getDimensionFromData(NbtCompound playerData) {
        return DimensionType.worldFromDimensionNbt(new Dynamic<>(NbtOps.INSTANCE, playerData.get("Dimension")))
                .resultOrPartial(LOGGER::error)
                .orElse(World.OVERWORLD);
    }

    @Inject(method = "savePlayerData", at = @At("HEAD"), cancellable = true)
    private void savePlayerData(ServerPlayerEntity player, CallbackInfo ci) {
        if (BuildOffSetup.mainGame.currentlyIn.contains(player.getUuid())) {
            ci.cancel();
        }
    }

    @Override
    public PlayerResetter buildoff$getPlayerResetter() {
        if (this.playerResetter == null) {
            var overworld = this.server.getOverworld();
            var profile = new GameProfile(Util.NIL_UUID, "null");

            var player = new ServerPlayerEntity(this.server, overworld, profile);
            this.statisticsMap.remove(Util.NIL_UUID);
            this.advancementTrackers.remove(Util.NIL_UUID);

            var tag = new NbtCompound();
            player.writeNbt(tag);
            tag.remove("UUID");
            tag.remove("Pos");

            this.playerResetter = new PlayerResetter(tag);
        }

        return this.playerResetter;
    }
}