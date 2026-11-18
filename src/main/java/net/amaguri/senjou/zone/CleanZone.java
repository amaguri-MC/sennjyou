package net.amaguri.senjou.zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/** 掃除区域。保存されるのは name/dimension/min/max/initial/remaining/cleared と、チャンク列ごとの残量(columns)。 */
public final class CleanZone {
	public static final Codec<CleanZone> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(z -> z.name),
			Codec.STRING.fieldOf("dimension").forGetter(z -> z.dimension),
			BlockPos.CODEC.fieldOf("min").forGetter(z -> z.min),
			BlockPos.CODEC.fieldOf("max").forGetter(z -> z.max),
			Codec.INT.fieldOf("initial").forGetter(z -> z.initial),
			Codec.INT.fieldOf("remaining").forGetter(z -> z.remaining),
			Codec.BOOL.fieldOf("cleared").forGetter(z -> z.cleared),
			Codec.LONG.listOf().optionalFieldOf("columns", List.of()).forGetter(CleanZone::columnsAsList)
	).apply(i, CleanZone::new));

	public final String name;
	/** ディメンションの Identifier 文字列（例 minecraft:overworld）。 */
	public final String dimension;
	public final BlockPos min;
	public final BlockPos max;
	public int initial;
	public int remaining;
	public boolean cleared;
	/** チャンク列(ZoneMath.columnKey) -> その列にある汚れ量。未ロードの列は前回値のまま残る。 */
	public final Map<Long, Integer> columns = new HashMap<>();

	// ---- 保存しない（実行時だけ）----
	/** 汚し・消去の作業中。true の間は数え直し・送信・クリア判定の対象外。 */
	public transient boolean busy;
	/** 数え直しの現在の周回。 */
	transient long lapIndex;
	transient boolean lapDone;
	transient Map<Long, Integer> lapSums = new HashMap<>();
	transient Map<Long, Integer> lapCounted = new HashMap<>();

	public CleanZone(String name, String dimension, BlockPos min, BlockPos max, int initial, int remaining, boolean cleared, List<Long> columnPairs) {
		this.name = name;
		this.dimension = dimension;
		this.min = min.immutable();
		this.max = max.immutable();
		this.initial = initial;
		this.remaining = remaining;
		this.cleared = cleared;
		for (int k = 0; k + 1 < columnPairs.size(); k += 2) {
			this.columns.put(columnPairs.get(k), (int) (long) columnPairs.get(k + 1));
		}
	}

	private List<Long> columnsAsList() {
		List<Long> out = new ArrayList<>(columns.size() * 2);
		for (Map.Entry<Long, Integer> e : columns.entrySet()) {
			out.add(e.getKey());
			out.add((long) e.getValue());
		}
		return out;
	}

	public int sizeX() {
		return max.getX() - min.getX() + 1;
	}

	public int sizeY() {
		return max.getY() - min.getY() + 1;
	}

	public int sizeZ() {
		return max.getZ() - min.getZ() + 1;
	}

	public long volume() {
		return ZoneMath.volume(sizeX(), sizeY(), sizeZ());
	}

	public int percent() {
		return ZoneMath.percent(initial, remaining);
	}

	/** 箱を margin ブロック広げた範囲に (x,y,z) が入っているか。 */
	public boolean containsExpanded(double x, double y, double z, int margin) {
		return x >= min.getX() - margin && x < max.getX() + 1 + margin
				&& y >= min.getY() - margin && y < max.getY() + 1 + margin
				&& z >= min.getZ() - margin && z < max.getZ() + 1 + margin;
	}

	public void resetLap() {
		lapIndex = 0;
		lapDone = false;
		lapSums.clear();
		lapCounted.clear();
	}

	public void setColumns(Map<Long, Integer> newColumns) {
		columns.clear();
		columns.putAll(newColumns);
	}
}
