package dev.basehunter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** What counts as a "player made" block and how much it contributes to a base score. */
public record BlockInfo(Category category, int weight) {
	public enum Category {
		/** Chests, barrels, shulkers... counted towards minChests. */
		CONTAINER,
		/** Furnaces, hoppers, anvils, beacons, valuables. */
		UTILITY,
		/** Cheap hints (redstone, beds...). Capped per chunk so villages don't light up. */
		DECOR
	}

	private static final Map<Block, BlockInfo> INFO = new IdentityHashMap<>();
	public static final Predicate<BlockState> IS_INTERESTING = state -> INFO.containsKey(state.getBlock());

	public static BlockInfo of(BlockState state) {
		return INFO.get(state.getBlock());
	}

	public static void init() {
		for (Block b : BuiltInRegistries.BLOCK) {
			BlockInfo info = classify(b);
			if (info != null) INFO.put(b, info);
		}
	}

	private static BlockInfo classify(Block b) {
		if (b instanceof ShulkerBoxBlock) return new BlockInfo(Category.CONTAINER, 5);
		if (b instanceof EnderChestBlock) return new BlockInfo(Category.CONTAINER, 4);
		if (b instanceof ChestBlock || b instanceof BarrelBlock) return new BlockInfo(Category.CONTAINER, 3);

		if (b == Blocks.BEACON) return new BlockInfo(Category.UTILITY, 10);
		if (b == Blocks.NETHERITE_BLOCK) return new BlockInfo(Category.UTILITY, 8);
		if (b == Blocks.DIAMOND_BLOCK || b == Blocks.EMERALD_BLOCK || b == Blocks.LODESTONE || b == Blocks.RESPAWN_ANCHOR)
			return new BlockInfo(Category.UTILITY, 4);
		if (b instanceof EnchantingTableBlock) return new BlockInfo(Category.UTILITY, 3);
		if (b instanceof HopperBlock || b instanceof DispenserBlock || b instanceof AbstractFurnaceBlock
			|| b instanceof BrewingStandBlock || b instanceof AnvilBlock)
			return new BlockInfo(Category.UTILITY, 2);

		String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
		if (id.equals("crafting_table") || id.endsWith("_bed") || id.contains("piston") && !id.contains("head")
			|| id.equals("observer") || id.equals("tnt") || b == Blocks.GOLD_BLOCK || b == Blocks.IRON_BLOCK)
			return new BlockInfo(Category.DECOR, 1);
		return null;
	}
}
