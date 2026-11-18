package net.amaguri.senjou.zone;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.amaguri.senjou.SenjouMod;

/** ワールドに保存される掃除区域の一覧（data/senjou/zones.dat）。 */
public final class ZoneData extends SavedData {
	public static final Codec<ZoneData> CODEC = RecordCodecBuilder.create(i -> i.group(
			CleanZone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(d -> d.zones)
	).apply(i, ZoneData::new));

	public static final SavedDataType<ZoneData> TYPE = new SavedDataType<>(SenjouMod.id("zones"), ZoneData::new, CODEC, DataFixTypes.LEVEL);

	public final List<CleanZone> zones = new ArrayList<>();

	public ZoneData() {
	}

	private ZoneData(List<CleanZone> loaded) {
		zones.addAll(loaded);
	}

	public static ZoneData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public CleanZone find(String name) {
		for (CleanZone z : zones) {
			if (z.name.equals(name)) {
				return z;
			}
		}
		return null;
	}

	/** 「区域N」で空いている最小の N の名前。 */
	public String nextDefaultName() {
		for (int n = 1;; n++) {
			String name = "区域" + n;
			if (find(name) == null) {
				return name;
			}
		}
	}
}
