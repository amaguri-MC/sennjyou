package net.amaguri.senjou.zone;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import net.amaguri.senjou.SenjouMod;

/** W2 の入口。汚しの杖の登録・コマンド・毎tickの処理を結びつける。 */
public final class ZoneSetup {
	private ZoneSetup() {
	}

	public static Item GRIME_WAND;

	public static void init() {
		Identifier wandId = SenjouMod.id("grime_wand");
		GRIME_WAND = Registry.register(BuiltInRegistries.ITEM, wandId,
				new GrimeWandItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, wandId)).stacksTo(1)));

		// クリエイティブ「道具と実用品」タブ
		ResourceKey<CreativeModeTab> tools = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
		CreativeModeTabEvents.modifyOutputEvent(tools).register(output -> output.accept(GRIME_WAND));

		// 杖を持って左クリック = 地点1（ブロックは壊さない）
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player.getItemInHand(hand).getItem() instanceof GrimeWandItem)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				WandSelection.setPos1(sp, pos);
			}
			return InteractionResult.FAIL;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> ZoneCommands.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(ZoneTracker::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ZoneTracker.onDisconnect(handler.getPlayer().getUUID());
			WandSelection.forget(handler.getPlayer().getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ZoneTracker.onServerStopped());
	}
}
