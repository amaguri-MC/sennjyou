package net.amaguri.senjou.client;

import net.fabricmc.api.ClientModInitializer;

import net.amaguri.senjou.client.grime.GrimeClient;
import net.amaguri.senjou.client.washer.WasherClient;
import net.amaguri.senjou.client.zone.ZoneClient;

/** Client entrypoint. CONTRACT FILE (owner: 司令塔). 変更しないこと。 */
public final class SenjouClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GrimeClient.init();  // W1: 汚れブロックの描画レイヤー等
		ZoneClient.init();   // W2: クリア率HUD・選択範囲の枠表示
		WasherClient.init(); // W3: 洗浄機の見た目・手元演出
	}
}
