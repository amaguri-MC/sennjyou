package net.amaguri.senjou.zone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** 汚しの杖で選んだ2点（プレイヤーごと・メモリ上のみ）と、その箱の枠のパーティクル。 */
public final class WandSelection {
	private WandSelection() {
	}

	private static final class Sel {
		String dimension;
		BlockPos p1;
		BlockPos p2;
	}

	private static final Map<UUID, Sel> SELECTIONS = new HashMap<>();
	private static final Map<UUID, Integer> LAST_CREATE = new HashMap<>();

	/** 枠の1辺あたりに出す粒の最大数。 */
	private static final int MAX_POINTS_PER_EDGE = 24;

	public static void setPos1(ServerPlayer p, BlockPos pos) {
		Sel s = sel(p);
		if (pos.equals(s.p1)) {
			return;
		}
		s.p1 = pos.immutable();
		p.sendOverlayMessage(Component.translatable("senjou.wand.pos1", pos.getX(), pos.getY(), pos.getZ()).append(volumeNote(s)));
	}

	public static void setPos2(ServerPlayer p, BlockPos pos) {
		Sel s = sel(p);
		if (pos.equals(s.p2)) {
			return;
		}
		s.p2 = pos.immutable();
		p.sendOverlayMessage(Component.translatable("senjou.wand.pos2", pos.getX(), pos.getY(), pos.getZ()).append(volumeNote(s)));
	}

	/** 選択済みの範囲を汚して区域にする（スニーク+右クリック）。 */
	public static void create(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		Sel s = SELECTIONS.get(p.getUUID());
		if (s == null || s.p1 == null || s.p2 == null) {
			p.sendOverlayMessage(Component.translatable("senjou.wand.need_points"));
			return;
		}
		int now = server.getTickCount();
		Integer last = LAST_CREATE.get(p.getUUID());
		if (last != null && now - last < 20) {
			return; // 右クリック長押しの連続発火を無視
		}
		LAST_CREATE.put(p.getUUID(), now);
		Component err = ZoneManager.startDirty(server, p.level(), s.p1, s.p2, null, p.getUUID());
		if (err != null) {
			p.sendOverlayMessage(err.copy().withStyle(net.minecraft.ChatFormatting.RED));
			return;
		}
		SELECTIONS.remove(p.getUUID());
		p.sendOverlayMessage(Component.translatable("senjou.wand.creating"));
	}

	public static void hint(ServerPlayer p) {
		p.sendOverlayMessage(Component.translatable("senjou.wand.hint"));
	}

	public static void clearAll() {
		SELECTIONS.clear();
		LAST_CREATE.clear();
	}

	public static void forget(UUID id) {
		SELECTIONS.remove(id);
		LAST_CREATE.remove(id);
	}

	private static Sel sel(ServerPlayer p) {
		Sel s = SELECTIONS.computeIfAbsent(p.getUUID(), k -> new Sel());
		String dim = p.level().dimension().identifier().toString();
		if (!dim.equals(s.dimension)) {
			s.dimension = dim;
			s.p1 = null;
			s.p2 = null;
		}
		return s;
	}

	private static Component volumeNote(Sel s) {
		if (s.p1 == null || s.p2 == null) {
			return Component.empty();
		}
		long vol = ZoneMath.volume(Math.abs(s.p1.getX() - s.p2.getX()) + 1, Math.abs(s.p1.getY() - s.p2.getY()) + 1, Math.abs(s.p1.getZ() - s.p2.getZ()) + 1);
		return Component.translatable("senjou.wand.volume", vol, ZoneMath.MAX_VOLUME);
	}

	/** 杖を持っているプレイヤーにだけ、選択の箱の辺を END_ROD で見せる。 */
	static void tickParticles(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!(p.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof GrimeWandItem)
					&& !(p.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof GrimeWandItem)) {
				continue;
			}
			Sel s = SELECTIONS.get(p.getUUID());
			if (s == null || (s.p1 == null && s.p2 == null) || !p.level().dimension().identifier().toString().equals(s.dimension)) {
				continue;
			}
			BlockPos a = s.p1 != null ? s.p1 : s.p2;
			BlockPos b = s.p2 != null ? s.p2 : s.p1;
			double x0 = Math.min(a.getX(), b.getX());
			double y0 = Math.min(a.getY(), b.getY());
			double z0 = Math.min(a.getZ(), b.getZ());
			double x1 = Math.max(a.getX(), b.getX()) + 1;
			double y1 = Math.max(a.getY(), b.getY()) + 1;
			double z1 = Math.max(a.getZ(), b.getZ()) + 1;
			for (int ix = 0; ix < 2; ix++) {
				for (int iy = 0; iy < 2; iy++) {
					edge(p, ix == 0 ? x0 : x1, iy == 0 ? y0 : y1, z0, ix == 0 ? x0 : x1, iy == 0 ? y0 : y1, z1); // Z方向
				}
			}
			for (int ix = 0; ix < 2; ix++) {
				for (int iz = 0; iz < 2; iz++) {
					edge(p, ix == 0 ? x0 : x1, y0, iz == 0 ? z0 : z1, ix == 0 ? x0 : x1, y1, iz == 0 ? z0 : z1); // Y方向
				}
			}
			for (int iy = 0; iy < 2; iy++) {
				for (int iz = 0; iz < 2; iz++) {
					edge(p, x0, iy == 0 ? y0 : y1, iz == 0 ? z0 : z1, x1, iy == 0 ? y0 : y1, iz == 0 ? z0 : z1); // X方向
				}
			}
		}
	}

	private static void edge(ServerPlayer p, double x0, double y0, double z0, double x1, double y1, double z1) {
		double len = Math.abs(x1 - x0) + Math.abs(y1 - y0) + Math.abs(z1 - z0);
		int n = (int) Math.max(1, Math.min(MAX_POINTS_PER_EDGE, Math.round(len)));
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			p.level().sendParticles(p, ParticleTypes.END_ROD, false, true, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
