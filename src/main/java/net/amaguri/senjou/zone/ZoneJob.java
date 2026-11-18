package net.amaguri.senjou.zone;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.amaguri.senjou.grime.Grime;

/**
 * 箱の中を何tickかに分けて処理する作業。ERASE(汚れを全部消す) → PAINT(汚す) → COUNT(汚れ量を数える) の順に好きな組み合わせで走らせる。
 */
final class ZoneJob {
	enum Phase {
		ERASE, PAINT, COUNT
	}

	/** 1tickに処理するマス数。汚す処理は canPlace×6 があるので少なめ。 */
	private static final int PAINT_PER_TICK = 4000;
	private static final int SCAN_PER_TICK = 20000;

	private final ResourceKey<Level> dimension;
	private final BlockPos min;
	private final BlockPos max;
	private final Deque<Phase> phases = new ArrayDeque<>();
	/** 作業が終わったら busy を外して結果を入れる区域。null なら（削除済みの区域の掃除など）何も更新しない。 */
	private final CleanZone zone;
	private final boolean setInitial;
	private final UUID requester;
	private final Function<CleanZone, Component> doneMessage;
	private final long seed;

	private final int sx;
	private final int sy;
	private final int sz;
	private final long volume;
	private long index;
	private long sum;
	private final Map<Long, Integer> counts = new HashMap<>();

	ZoneJob(ResourceKey<Level> dimension, BlockPos min, BlockPos max, CleanZone zone, boolean setInitial, UUID requester, long seed, Function<CleanZone, Component> doneMessage, Phase... order) {
		this.dimension = dimension;
		this.min = min;
		this.max = max;
		this.zone = zone;
		this.setInitial = setInitial;
		this.requester = requester;
		this.seed = seed;
		this.doneMessage = doneMessage;
		for (Phase p : order) {
			phases.add(p);
		}
		this.sx = max.getX() - min.getX() + 1;
		this.sy = max.getY() - min.getY() + 1;
		this.sz = max.getZ() - min.getZ() + 1;
		this.volume = ZoneMath.volume(sx, sy, sz);
	}

	/** 1tick分進める。全部終わったら true。 */
	boolean tick(MinecraftServer server, ZoneData data) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			finish(server, data, false);
			return true;
		}
		Phase phase = phases.peek();
		if (phase == null) {
			finish(server, data, true);
			return true;
		}
		int budget = phase == Phase.PAINT ? PAINT_PER_TICK : SCAN_PER_TICK;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (budget > 0 && index < volume) {
			long idx = index++;
			budget--;
			int y = (int) (idx % sy);
			long t = idx / sy;
			int z = (int) (t % sz);
			int x = (int) (t / sz);
			pos.set(min.getX() + x, min.getY() + y, min.getZ() + z);
			switch (phase) {
				case ERASE -> {
					BlockState state = level.getBlockState(pos);
					if (Grime.isGrime(state)) {
						Grime.remove(level, pos);
					}
				}
				case PAINT -> paintCell(level, pos);
				case COUNT -> {
					int amount = Grime.amount(level.getBlockState(pos));
					if (amount > 0) {
						sum += amount;
						counts.merge(ZoneMath.columnKey(pos.getX() >> 4, pos.getZ() >> 4), amount, Integer::sum);
					}
				}
			}
		}
		if (index >= volume) {
			phases.poll();
			index = 0;
			if (phase == Phase.COUNT) {
				applyCount();
			}
			if (phases.isEmpty()) {
				finish(server, data, true);
				return true;
			}
		}
		return false;
	}

	private void paintCell(ServerLevel level, BlockPos.MutableBlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.isAir() && !Grime.isGrime(state)) {
			return;
		}
		for (Direction face : Direction.values()) {
			int lvl = ZoneMath.levelAt(seed, pos.getX(), pos.getY(), pos.getZ(), face.ordinal());
			if (lvl > 0 && Grime.canPlace(level, pos, face)) {
				Grime.place(level, pos, face, lvl);
			}
		}
	}

	private void applyCount() {
		if (zone == null) {
			return;
		}
		int total = (int) Math.min(Integer.MAX_VALUE, sum);
		zone.setColumns(counts);
		zone.remaining = total;
		if (setInitial) {
			zone.initial = total;
			zone.cleared = false;
		}
		zone.resetLap();
	}

	private void finish(MinecraftServer server, ZoneData data, boolean ok) {
		if (zone != null) {
			zone.busy = false;
			data.setDirty();
		}
		if (ok && requester != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(requester);
			if (p != null && doneMessage != null) {
				p.sendSystemMessage(doneMessage.apply(zone));
			}
		}
	}
}
