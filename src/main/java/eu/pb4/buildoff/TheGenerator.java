package eu.pb4.buildoff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class TheGenerator extends VoidChunkGenerator {
    public static final Codec<VoidChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Biome.REGISTRY_CODEC.stable().fieldOf("biome").forGetter(g -> g.getBiomeForNoiseGen(0, 0, 0))
        ).apply(instance, instance.stable(TheGenerator::new));
    });

    public TheGenerator(RegistryEntry<Biome> biome) {
        super(biome);
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public ChunkGenerator withSeed(long seed) {
        return this;
    }

    @Override
    public int getMinimumY() {
        return 0;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender, StructureAccessor structureAccessor, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            var pos = new BlockPos.Mutable();
            if (chunk.getPos().x == -1 || chunk.getPos().z == -1 || chunk.getPos().z == 4) {
                for (int y = 0; y < chunk.getHeight(); y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            chunk.setBlockState(pos.set(x, y, z), Blocks.BARRIER.getDefaultState(), false);
                        }
                    }
                }
            } else if (chunk.getPos().x < -1 || chunk.getPos().z < -1 || chunk.getPos().z > 4) {

            } else {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.setBlockState(pos.set(x, 0, z), Blocks.BEDROCK.getDefaultState(), false);

                        int rX = x + chunk.getPos().x * 16;
                        int rZ = z + chunk.getPos().z * 16;

                        int pX = rX % 64;
                        if (pX < 0) {
                            pX += 64;
                        }

                        int pZ = rZ % 64;
                        if (pZ < 0) {
                            pZ += 64;
                        }

                        if ((pX == 8 && pZ >= 8 && pZ <= 56) || (pZ == 8 && pX >= 8 && pX <= 56) || (pX == 56 && pZ >= 8 && pZ <= 56) || (pZ == 56 && pX >= 8 && pX <= 56)) {
                            for (int y = 1; y < 33; y++) {
                                chunk.setBlockState(pos.set(x, y, z), Blocks.BEDROCK.getDefaultState(), false);
                            }

                            chunk.setBlockState(pos.set(x, 33, z), BuildOffSetup.HARD_SLAB_BLOCK.getDefaultState(), false);

                            for (int y = 34; y < chunk.getHeight(); y++) {
                                chunk.setBlockState(pos.set(x, y, z), BuildOffSetup.HARD_BLOCK.getDefaultState(), false);
                            }
                        } else {
                            for (int y = 1; y < 29; y++) {
                                chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.getDefaultState(), false);
                            }

                            chunk.setBlockState(pos.set(x, 29, z), Blocks.DIRT.getDefaultState(), false);
                            chunk.setBlockState(pos.set(x, 30, z), Blocks.DIRT.getDefaultState(), false);
                            chunk.setBlockState(pos.set(x, 31, z), Blocks.DIRT.getDefaultState(), false);

                            if (pX < 8 || pZ < 8 || pX > 56 || pZ > 56) {
                                chunk.setBlockState(pos.set(x, 32, z), ((x + z) % 2 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.OAK_LOG).getDefaultState(), false);
                            } else {
                                chunk.setBlockState(pos.set(x, 32, z), Blocks.GRASS_BLOCK.getDefaultState(), false);
                            }
                        }
                    }
                }
            }

            if (chunk.getPos().x == 0 && chunk.getPos().z == 0) {
                var entity = new TeleportingEntity(BuildOffSetup.world.asWorld());
                entity.setPos(5, 33, 5);
                chunk.addEntity(entity);
            }

            return chunk;
        }, executor);
    }
}
