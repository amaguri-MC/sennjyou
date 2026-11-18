package net.amaguri.senjou.washer;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.UseEffects;

import net.amaguri.senjou.SenjouMod;

/** W3: 高圧洗浄機の登録。 */
public final class WasherSetup {
	private WasherSetup() {
	}

	public static Item PRESSURE_WASHER;

	public static void init() {
		Identifier id = SenjouMod.id("pressure_washer");
		PRESSURE_WASHER = Registry.register(BuiltInRegistries.ITEM, id,
				new PressureWasherItem(new Item.Properties()
						.setId(ResourceKey.create(Registries.ITEM, id))
						.stacksTo(1)
						// 噴射中は歩ける（走れない・少し遅い）。
						.component(DataComponents.USE_EFFECTS, new UseEffects(false, false, 0.6f))));

		// クリエイティブ「道具と実用品」タブ。
		ResourceKey<CreativeModeTab> tools = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
				Identifier.withDefaultNamespace("tools_and_utilities"));
		CreativeModeTabEvents.modifyOutputEvent(tools).register(output -> output.accept(PRESSURE_WASHER));

		// 貯まり量の掃除（使うのをやめたプレイヤーの分）。
		ServerTickEvents.END_SERVER_TICK.register(server -> SprayState.sweep(server.getTickCount()));
	}
}
