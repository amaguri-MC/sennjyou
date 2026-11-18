package net.amaguri.senjou.zone;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** 区域の作成・削除・やり直しと、その作業(ZoneJob)の実行。結果は Component（エラー）か null（成功）で返す。 */
public final class ZoneManager {
	private ZoneManager() {
	}

	private static final Deque<ZoneJob> JOBS = new ArrayDeque<>();

	/** 箱(2点)を汚して新しい区域にする。name が null/空なら「区域N」。エラーなら理由、成功なら null。 */
	public static Component startDirty(MinecraftServer server, ServerLevel level, BlockPos a, BlockPos b, String name, UUID requester) {
		ZoneData data = ZoneData.get(server);
		if (name == null || name.isBlank()) {
			name = data.nextDefaultName();
		}
		name = name.strip();
		if (data.find(name) != null) {
			return Component.translatable("senjou.zone.exists", name);
		}
		int minY = Math.max(level.getMinY(), Math.min(a.getY(), b.getY()));
		int maxY = Math.min(level.getMaxY(), Math.max(a.getY(), b.getY()));
		if (minY > maxY) {
			return Component.translatable("senjou.zone.out_of_height");
		}
		BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), minY, Math.min(a.getZ(), b.getZ()));
		BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), maxY, Math.max(a.getZ(), b.getZ()));
		long vol = ZoneMath.volume(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
		if (vol > ZoneMath.MAX_VOLUME) {
			return Component.translatable("senjou.zone.too_big", vol, ZoneMath.MAX_VOLUME);
		}
		if (!level.hasChunksAt(min, max)) {
			return Component.translatable("senjou.zone.not_loaded");
		}
		CleanZone zone = new CleanZone(name, level.dimension().identifier().toString(), min, max, 0, 0, false, java.util.List.of());
		zone.busy = true;
		data.zones.add(zone);
		data.setDirty();
		queuePaint(level, zone, requester, false);
		return null;
	}

	/** 中の汚れを消して汚し直す。 */
	public static Component reset(MinecraftServer server, CleanZone zone, UUID requester) {
		ServerLevel level = levelOf(server, zone);
		if (level == null) {
			return Component.translatable("senjou.zone.no_level", zone.name);
		}
		if (zone.busy) {
			return Component.translatable("senjou.zone.busy", zone.name);
		}
		if (!level.hasChunksAt(zone.min, zone.max)) {
			return Component.translatable("senjou.zone.not_loaded");
		}
		zone.busy = true;
		zone.initial = 0;
		zone.remaining = 0;
		zone.cleared = false;
		zone.columns.clear();
		zone.resetLap();
		ZoneData.get(server).setDirty();
		queuePaint(level, zone, requester, true);
		return null;
	}

	/** デバッグ: 中の汚れを全部消す（数え直しで0になり、クリア演出が出る）。 */
	public static Component clean(MinecraftServer server, CleanZone zone, UUID requester) {
		ServerLevel level = levelOf(server, zone);
		if (level == null) {
			return Component.translatable("senjou.zone.no_level", zone.name);
		}
		if (zone.busy) {
			return Component.translatable("senjou.zone.busy", zone.name);
		}
		if (!level.hasChunksAt(zone.min, zone.max)) {
			return Component.translatable("senjou.zone.not_loaded");
		}
		zone.busy = true;
		JOBS.add(new ZoneJob(level.dimension(), zone.min, zone.max, zone, false, requester, 0L,
				z -> Component.translatable("senjou.zone.cleaned", zone.name),
				ZoneJob.Phase.ERASE, ZoneJob.Phase.COUNT));
		return null;
	}

	/** 区域と中の汚れを消す。 */
	public static Component remove(MinecraftServer server, CleanZone zone, UUID requester) {
		ServerLevel level = levelOf(server, zone);
		if (zone.busy) {
			return Component.translatable("senjou.zone.busy", zone.name);
		}
		if (level != null && !level.hasChunksAt(zone.min, zone.max)) {
			return Component.translatable("senjou.zone.not_loaded");
		}
		ZoneData data = ZoneData.get(server);
		data.zones.remove(zone);
		data.setDirty();
		if (level != null) {
			String name = zone.name;
			JOBS.add(new ZoneJob(level.dimension(), zone.min, zone.max, null, false, requester, 0L,
					z -> Component.translatable("senjou.zone.removed", name),
					ZoneJob.Phase.ERASE));
		}
		return null;
	}

	public static ServerLevel levelOf(MinecraftServer server, CleanZone zone) {
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().identifier().toString().equals(zone.dimension)) {
				return l;
			}
		}
		return null;
	}

	private static void queuePaint(ServerLevel level, CleanZone zone, UUID requester, boolean erase) {
		long seed = level.getRandom().nextLong();
		JOBS.add(new ZoneJob(level.dimension(), zone.min, zone.max, zone, true, requester, seed,
				z -> z.initial > 0
						? Component.translatable("senjou.zone.done", z.name, z.initial)
						: Component.translatable("senjou.zone.done_empty", z.name),
				erase ? new ZoneJob.Phase[] { ZoneJob.Phase.ERASE, ZoneJob.Phase.PAINT, ZoneJob.Phase.COUNT }
						: new ZoneJob.Phase[] { ZoneJob.Phase.PAINT, ZoneJob.Phase.COUNT }));
	}

	/** 先頭の作業を1tick分進める。 */
	static void tickJobs(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		ZoneData data = ZoneData.get(server);
		ZoneJob job = JOBS.peek();
		if (job.tick(server, data)) {
			JOBS.poll();
		}
	}

	/** サーバー停止時: 残りの作業は捨てる（busy は保存されないので次回起動で通常に戻る）。 */
	static void clearJobs() {
		JOBS.clear();
	}

	static int pendingJobs() {
		return JOBS.size();
	}
}
