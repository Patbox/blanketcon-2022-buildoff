package eu.pb4.buildoff;

import eu.pb4.buildoff.items.WrappedItem;
import eu.pb4.polymer.api.entity.PolymerEntityUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.EventListenerMap;
import xyz.nucleoid.stimuli.event.StimulusEvent;
import xyz.nucleoid.stimuli.filter.EventFilter;
import xyz.nucleoid.stimuli.selector.SimpleListenerSelector;

public class BuildOffSetup implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("modid");
	public static RuntimeWorldHandle world;

	public static final Item WRAPPED = new WrappedItem(new Item.Settings());
	public static final Block HARD_BLOCK = new HardInvisibleBlock(FabricBlockSettings.of(Material.STONE).suffocates((x, y, z) -> false).hardness(-1));
	public static final Block HARD_SLAB_BLOCK = new HardSlabBlock(FabricBlockSettings.of(Material.STONE).suffocates((x, y, z) -> false).hardness(-1));

	public static GameRules.Key<GameRules.BooleanRule> CAN_BUILD
			= GameRuleRegistry.register("buildoff:can_build", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	
	public static MainGame mainGame;

	@Override
	public void onInitialize() {
		Registry.register(Registry.CHUNK_GENERATOR, new Identifier("buildoff", "generator"), TheGenerator.CODEC);
		Registry.register(Registry.ITEM, new Identifier("buildoff", "wrapped"), WRAPPED);
		Registry.register(Registry.BLOCK, new Identifier("buildoff", "hard_air"), HARD_BLOCK);
		Registry.register(Registry.BLOCK, new Identifier("buildoff", "hard_slab"), HARD_SLAB_BLOCK);
		Registry.register(Registry.ENTITY_TYPE, new Identifier("buildoff", "janek_teleporter"), TeleportingEntity.TYPE);

		FabricDefaultAttributeRegistry.register(TeleportingEntity.TYPE, TeleportingEntity.createMobAttributes());
		PolymerEntityUtils.registerType(TeleportingEntity.TYPE);

		ServerLifecycleEvents.SERVER_STARTED.register((s) -> {
			var world = Fantasy.get(s).getOrOpenPersistentWorld(new Identifier("buildoff", "world"),
					new RuntimeWorldConfig()
							.setDifficulty(Difficulty.PEACEFUL)
							.setGameRule(GameRules.DO_MOB_SPAWNING, false)
							.setGameRule(GameRules.DO_DAYLIGHT_CYCLE, false)
							.setTimeOfDay(12000)
							.setDimensionType(DimensionType.OVERWORLD_REGISTRY_KEY)
							.setGenerator(new TheGenerator(s.getRegistryManager().get(Registry.BIOME_KEY).getEntry(BiomeKeys.FOREST).get()))
			);
			
			BuildOffSetup.world = world;
			var events = new EventListenerMap();
			BuildOffSetup.mainGame = new MainGame(s, world.asWorld(), events);
			Stimuli.registerSelector(new SimpleListenerSelector(EventFilter.dimension(world.getRegistryKey()), events));
		});

		ServerTickEvents.END_SERVER_TICK.register((server) -> BuildOffSetup.mainGame.tick());
		ServerLifecycleEvents.SERVER_STOPPING.register((server) -> BuildOffSetup.mainGame.save());
		ServerLifecycleEvents.SERVER_STOPPED.register((server) -> {
			BuildOffSetup.mainGame = null;
			BuildOffSetup.world = null;
		});
	}

	public static StimulusEvent<BucketUsage> ON_BUCKET_USAGE = StimulusEvent.create(BucketUsage.class, ctx -> (player, blockPos) -> {
		try {
			for (var listener : ctx.getListeners()) {
				var result = listener.onUse(player, blockPos);
				if (result != ActionResult.PASS) {
					return result;
				}
			}
		} catch (Throwable t) {
			ctx.handleException(t);
		}
		return ActionResult.PASS;
	});


	public interface BucketUsage {
		ActionResult onUse(ServerPlayerEntity player, BlockPos pos);
	}
}
