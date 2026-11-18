package net.amaguri.senjou;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import net.amaguri.senjou.grime.GrimeSetup;
import net.amaguri.senjou.net.SenjouNetwork;
import net.amaguri.senjou.washer.WasherSetup;
import net.amaguri.senjou.zone.ZoneSetup;

/** Main entrypoint. CONTRACT FILE (owner: 司令塔). 変更しないこと。 */
public final class SenjouMod implements ModInitializer {
	public static final String MOD_ID = "senjou";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		SenjouNetwork.registerPayloads();
		GrimeSetup.init();  // W1: 汚れブロック
		ZoneSetup.init();   // W2: 掃除区域・汚しの杖・コマンド・クリア率送信
		WasherSetup.init(); // W3: 高圧洗浄機
	}
}
