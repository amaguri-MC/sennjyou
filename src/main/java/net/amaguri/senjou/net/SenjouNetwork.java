package net.amaguri.senjou.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** CONTRACT FILE (owner: 司令塔). */
public final class SenjouNetwork {
	private SenjouNetwork() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.clientboundPlay().register(ZoneProgressPayload.TYPE, ZoneProgressPayload.CODEC);
	}
}
