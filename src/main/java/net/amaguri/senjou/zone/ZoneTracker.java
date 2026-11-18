package net.amaguri.senjou.zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import net.amaguri.senjou.grime.Grime;
import net.amaguri.senjou.net.ZoneProgressPayload;

/** 毎tick: 残量の数え直し・クリア判定・進捗の送信。 */
public final class ZoneTracker {
	private ZoneTracker() {
	}

	/** 全区域あわせて1tickに読むマス数の上限。 */
	static final int RECOUNT_BUDGET = 8192;
	/** 送信間隔(tick)と、区域の「近く」とみなす広げ幅(ブロック)。 */
	static final int SEND_INTERVAL = 10;
	static final int SEND_MARGIN = 6;
	/** クリア演出を見せる範囲(箱を広げる幅)。 */
	static final int FANFARE_MARGIN = 16;

	private static int cursorZone;
	/** 今 HUD を出している（active=true を送った）プレイヤー。 */
	private static final Set<UUID> ACTIVE = new HashSet<>();

	public static void tick(MinecraftServer server) {
		ZoneManager.tickJobs(server);
		ZoneData data = ZoneData.get(server);
		if (!data.zones.isEmpty()) {
			recount(server, data);
			checkCleared(server, data);
		}
		int t = server.getTickCount();
		if (t % SEND_INTERVAL == 0) {
			sendProgress(server, data);
		}
		if (t % SEND_INTERVAL == 5) {
			WandSelection.tickParticles(server);
		}
	}

	public static void onDisconnect(UUID id) {
		ACTIVE.remove(id);
	}

	public static void onServerStopped() {
		ACTIVE.clear();
		cursorZone = 0;
		ZoneManager.clearJobs();
		WandSelection.clearAll();
	}

	// ---- 残量の数え直し ----

	private static void recount(MinecraftServer server, ZoneData data) {
		int budget = RECOUNT_BUDGET;
		int guard = data.zones.size() + 1;
		boolean first = true;
		while (budget > 0 && guard-- > 0) {
			if (cursorZone >= data.zones.size()) {
				cursorZone = 0;
			}
			CleanZone zone = data.zones.get(cursorZone);
			ServerLevel level = zone.busy ? null : ZoneManager.levelOf(server, zone);
			if (level == null) {
				cursorZone++;
				continue;
			}
			int spent = recountZone(level, zone, budget, first, data);
			budget -= spent;
			first = false;
			if (!zone.lapDone) {
				break; // 今tickの分を使い切った。続きは次のtick。
			}
			zone.lapDone = false;
			cursorZone++; // 1周終わった。次の区域へ（1区域は1tickに最大1周）。
		}
	}

	/** 区域を最大 budget マス分読み進める。使ったマス数を返す。1周し終えたら lapIndex は 0 に戻り remaining を更新する。 */
	private static int recountZone(ServerLevel level, CleanZone zone, int budget, boolean mustProgress, ZoneData data) {
		int sx = zone.sizeX();
		int sy = zone.sizeY();
		int sz = zone.sizeZ();
		long cols = (long) sx * sz;
		int spent = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (zone.lapIndex < cols) {
			int x = (int) (zone.lapIndex / sz);
			int z = (int) (zone.lapIndex % sz);
			int wx = zone.min.getX() + x;
			int wz = zone.min.getZ() + z;
			pos.set(wx, zone.min.getY(), wz);
			boolean loaded = level.hasChunkAt(pos);
			int cost = loaded ? sy : 1;
			if (spent + cost > budget && !(mustProgress && spent == 0)) {
				return spent;
			}
			if (loaded) {
				int sum = 0;
				for (int y = 0; y < sy; y++) {
					pos.set(wx, zone.min.getY() + y, wz);
					BlockState state = level.getBlockState(pos);
					sum += Grime.amount(state);
				}
				long key = ZoneMath.columnKey(wx >> 4, wz >> 4);
				zone.lapSums.merge(key, sum, Integer::sum);
				zone.lapCounted.merge(key, 1, Integer::sum);
			}
			spent += cost;
			zone.lapIndex++;
		}
		finishLap(level, zone, data);
		return spent;
	}

	/** 1周分を反映する。全部の列を読めたチャンクだけ更新し、残り（未ロード）は前回値のまま。 */
	private static void finishLap(ServerLevel level, CleanZone zone, ZoneData data) {
		int before = zone.remaining;
		for (Map.Entry<Long, Integer> e : zone.lapCounted.entrySet()) {
			long key = e.getKey();
			int cx = ZoneMath.columnX(key);
			int cz = ZoneMath.columnZ(key);
			int w = Math.min(zone.max.getX(), cx * 16 + 15) - Math.max(zone.min.getX(), cx * 16) + 1;
			int d = Math.min(zone.max.getZ(), cz * 16 + 15) - Math.max(zone.min.getZ(), cz * 16) + 1;
			if (e.getValue() >= w * d && level.hasChunkAt(new BlockPos(cx * 16, zone.min.getY(), cz * 16))) {
				zone.columns.put(key, zone.lapSums.getOrDefault(key, 0));
			}
		}
		long total = 0;
		for (int v : zone.columns.values()) {
			total += v;
		}
		zone.remaining = (int) Math.min(Integer.MAX_VALUE, total);
		zone.lapIndex = 0;
		zone.lapDone = true;
		zone.lapSums = new HashMap<>();
		zone.lapCounted = new HashMap<>();
		if (zone.remaining != before) {
			data.setDirty();
		}
	}

	// ---- クリア演出 ----

	private static void checkCleared(MinecraftServer server, ZoneData data) {
		for (CleanZone zone : data.zones) {
			if (!zone.busy && !zone.cleared && zone.initial > 0 && zone.remaining <= 0) {
				zone.cleared = true;
				data.setDirty();
				fanfare(server, zone);
			}
		}
	}

	private static void fanfare(MinecraftServer server, CleanZone zone) {
		ServerLevel level = ZoneManager.levelOf(server, zone);
		if (level == null) {
			return;
		}
		Component title = Component.translatable("senjou.zone.clear.title");
		Component subtitle = Component.literal(zone.name);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level() != level || !zone.containsExpanded(p.getX(), p.getY(), p.getZ(), FANFARE_MARGIN)) {
				continue;
			}
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
			p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() + 1.0, p.getZ(), 40, 1.2, 0.8, 1.2, 0.08);
		}
		double cx = (zone.min.getX() + zone.max.getX() + 1) / 2.0;
		double cz = (zone.min.getZ() + zone.max.getZ() + 1) / 2.0;
		double top = zone.max.getY() + 1.5;
		double spreadX = Math.max(1.0, zone.sizeX() / 3.0);
		double spreadZ = Math.max(1.0, zone.sizeZ() / 3.0);
		level.sendParticles(ParticleTypes.FIREWORK, cx, top, cz, 120, spreadX, 1.5, spreadZ, 0.15);
	}

	// ---- 進捗の送信 ----

	private static void sendProgress(MinecraftServer server, ZoneData data) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			CleanZone best = null;
			double bestD = Double.MAX_VALUE;
			String dim = p.level().dimension().identifier().toString();
			for (CleanZone z : data.zones) {
				if (z.busy || !z.dimension.equals(dim) || !z.containsExpanded(p.getX(), p.getY(), p.getZ(), SEND_MARGIN)) {
					continue;
				}
				double dx = p.getX() - (z.min.getX() + z.max.getX() + 1) / 2.0;
				double dy = p.getY() - (z.min.getY() + z.max.getY() + 1) / 2.0;
				double dz = p.getZ() - (z.min.getZ() + z.max.getZ() + 1) / 2.0;
				double d = dx * dx + dy * dy + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = z;
				}
			}
			if (best != null) {
				ServerPlayNetworking.send(p, new ZoneProgressPayload(true, best.name, best.initial, best.remaining));
				ACTIVE.add(p.getUUID());
			} else if (ACTIVE.remove(p.getUUID())) {
				ServerPlayNetworking.send(p, ZoneProgressPayload.INACTIVE);
			}
		}
	}
}
