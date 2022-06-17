package eu.pb4.buildoff;

import eu.pb4.holograms.api.Holograms;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.map_templates.BlockBounds;

import java.util.*;

public class BuildArena {
    public final BlockBounds buildingArea;
    public final BlockBounds spawn;
    public final Set<UUID> playersUuid = new HashSet<>();
    public final int id;
    public int score = 0;

    public BuildArena(int id, BlockBounds area,  BlockBounds spawn) {
        this.id = id;
        this.buildingArea = area;
        this.spawn = spawn;
    }

    public boolean canBuild(BlockPos blockPos, ServerPlayerEntity player) {
        return this.playersUuid.contains(player.getUuid()) && this.buildingArea.contains(blockPos);
    }

    /*public Text getBuildersText(GameSpace gameSpace) {
        if (this.players.isEmpty()) {
            return new TranslatableText("text.buildbattle.nobody").formatted(Formatting.GRAY).formatted(Formatting.ITALIC);
        } else {
            MutableText text = new LiteralText("").formatted(Formatting.WHITE);

            for (PlayerData playerData : this.players) {
                int index = this.players.indexOf(playerData);
                if (index != 0) {
                    if (this.players.size() - index == 1) {
                        text.append(new TranslatableText("text.buildbattle.and").formatted(Formatting.GOLD));
                    } else {
                        text.append(new LiteralText(", ").formatted(Formatting.GOLD));
                    }
                }

                ServerPlayerEntity player = playerData.playerRef.getEntity(gameSpace.getServer());

                if (player != null) {
                    text.append(player.getDisplayName());
                } else {
                    text.append(new TranslatableText("text.buildbattle.disconnected").formatted(Formatting.GRAY).formatted(Formatting.ITALIC));
                }
            }
            return text;
        }
    }*/

    public void addPlayer(ServerPlayerEntity player) {
        this.playersUuid.add(player.getUuid());
        //this.players.add(data);
    }

    public void spawnEntity(ServerWorld world, float yaw) {

    }

    public void removeEntity() {

    }

    public List<ServerPlayerEntity> getPlayersInArena(ServerWorld world) {
        return world.getPlayers((player) -> this.buildingArea.contains(player.getBlockPos()));
    }

    public void teleportPlayer(ServerPlayerEntity player, ServerWorld world) {
        double x = MathHelper.nextDouble(player.getRandom(), this.spawn.min().getX(), this.spawn.max().getX());
        double y = this.spawn.min().getY();
        double z = MathHelper.nextDouble(player.getRandom(), this.spawn.min().getZ(), this.spawn.max().getZ());

        player.teleport(world, x, y, z, player.getYaw(), player.getPitch());
    }

    public boolean isBuilder(PlayerEntity player) {
        return player instanceof ServerPlayerEntity && this.playersUuid.contains(player.getUuid());
    }


    public int getPlayerCount() {
        return this.playersUuid.size();
    }

    public void updateHologram(ServerWorld world, MinecraftServer server) {
    }

    public void updateHologram(MainGame mainGame) {
        var lines = new ArrayList<Text>();
        for (var uuid : this.playersUuid) {
            var opt = mainGame.server.getUserCache().getByUuid(uuid);

            lines.add(new LiteralText(opt.isEmpty() ? "Unknown Player (" + uuid + ")" : opt.get().getName()));

        }

        var holo = mainGame.holograms.get(this.id);

        if (holo == null) {
            holo = Holograms.create(mainGame.world, this.spawn.center().add(0, 2, 0),lines.toArray(new Text[0]));
            holo.show();
            mainGame.holograms.set(holo, this.id);
        } else {
            holo.clearElements();
            for (var line : lines) {
                holo.addText(line);
            }
        }
    }
}
