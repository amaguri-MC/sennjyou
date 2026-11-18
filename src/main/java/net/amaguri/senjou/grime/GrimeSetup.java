package net.amaguri.senjou.grime;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.amaguri.senjou.SenjouMod;

/** 汚れブロックと BlockItem の登録。 */
public final class GrimeSetup {
	private GrimeSetup() {
	}

	public static Block GRIME;
	public static Item GRIME_ITEM;

	public static void init() {
		Identifier id = SenjouMod.id("grime");
		GRIME = Registry.register(BuiltInRegistries.BLOCK, id, new GrimeBlock(BlockBehaviour.Properties.of()
				.setId(ResourceKey.create(Registries.BLOCK, id))
				.noCollision()
				.replaceable()
				.instabreak()
				.noLootTable()
				.sound(SoundType.MUD)));
		GRIME_ITEM = Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(GRIME,
				new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));

		ResourceKey<CreativeModeTab> tools = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
				Identifier.withDefaultNamespace("tools_and_utilities"));
		CreativeModeTabEvents.modifyOutputEvent(tools).register(output -> output.accept(GRIME_ITEM));
	}
}
