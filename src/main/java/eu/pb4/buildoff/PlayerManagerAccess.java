package eu.pb4.buildoff;

import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerManagerAccess {
    void buildoff$savePlayerData(ServerPlayerEntity player);

    void buildoff$loadIntoPlayer(ServerPlayerEntity player);

    PlayerResetter buildoff$getPlayerResetter();
}