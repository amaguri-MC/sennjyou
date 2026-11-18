package net.amaguri.senjou;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public final class SenjouMod implements ModInitializer {
	public static final String MOD_ID = "senjou";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
	}
}
