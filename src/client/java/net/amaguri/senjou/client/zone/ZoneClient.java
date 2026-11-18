package net.amaguri.senjou.client.zone;

import java.lang.reflect.Field;
import java.util.Map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import net.amaguri.senjou.SenjouMod;
import net.amaguri.senjou.net.ZoneProgressPayload;
import net.amaguri.senjou.zone.ZoneMath;

/**
 * 近くの掃除区域のクリア率を画面上中央に出す（ボスバーがあればその下）。
 * 黒っぽい半透明の帯 + 1px罫線 + 細い横棒。100%で文字が金色。F1/F3中は隠す。
 * サーバーは10tickおきに送る。3秒届かなければ（切断・不具合の保険として）隠す。
 */
public final class ZoneClient {
	private ZoneClient() {
	}

	private static final long STALE_MS = 3000L;
	private static final int BAND = 0xB0000000;
	private static final int LINE = 0xFF3C3C3C;
	private static final int TRACK = 0xFF303030;
	private static final int BAR = 0xFFC8D4DC;
	private static final int BAR_DONE = 0xFFFFD35A;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_SUB = 0xFFB8B8B8;
	private static final int GOLD = 0xFFFFD35A;

	private static ZoneProgressPayload current = ZoneProgressPayload.INACTIVE;
	private static long receivedAt;
	private static Field bossEvents;
	private static boolean bossReflectTried;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZoneProgressPayload.TYPE, (payload, context) -> {
			current = payload;
			receivedAt = Util.getMillis();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> current = ZoneProgressPayload.INACTIVE);
		// 画面の既存要素のあとに付ける（F1で隠れる）
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, SenjouMod.id("zone_hud"), ZoneClient::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ZoneProgressPayload p = current;
		if (!p.active() || Util.getMillis() - receivedAt > STALE_MS) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = mc.font;
		int pct = ZoneMath.percent(p.initial(), p.remaining());
		boolean done = pct >= 100;
		Component name = Component.literal(clip(font, p.name().isEmpty() ? "-" : p.name(), 180));
		Component rate = Component.translatable("senjou.hud.clear_rate", pct);

		int lineH = font.lineHeight;
		int textW = Math.max(font.width(name), font.width(rate));
		int halfW = Math.max(52, textW / 2 + 12);
		int boxH = 4 + lineH + 1 + lineH + 3 + 2 + 4;
		int cx = graphics.guiWidth() / 2;
		int top = bossBarsBottom(mc);
		int x0 = cx - halfW;
		int x1 = cx + halfW;
		int y1 = top + boxH;

		graphics.fill(x0, top, x1, y1, BAND);
		graphics.fill(x0, top, x1, top + 1, LINE);
		graphics.fill(x0, y1 - 1, x1, y1, LINE);
		graphics.fill(x0, top, x0 + 1, y1, LINE);
		graphics.fill(x1 - 1, top, x1, y1, LINE);

		int y = top + 4;
		graphics.text(font, name, cx - font.width(name) / 2, y, TEXT_SUB, true);
		y += lineH + 1;
		graphics.text(font, rate, cx - font.width(rate) / 2, y, done ? GOLD : TEXT, true);
		y += lineH + 3;

		int bx0 = x0 + 6;
		int bx1 = x1 - 6;
		graphics.fill(bx0, y, bx1, y + 2, TRACK);
		int w = (int) ((bx1 - bx0) * (long) pct / 100L);
		if (w > 0) {
			graphics.fill(bx0, y, bx0 + w, y + 2, done ? BAR_DONE : BAR);
		}
	}

	private static String clip(Font font, String s, int maxWidth) {
		if (font.width(s) <= maxWidth) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && font.width(t + "…") > maxWidth) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}

	/** ボスバー群の下端のy。ボスバーが無ければ 0。1本あたり約19px（バニラは y=12 から 19px おき）。 */
	private static int bossBarsBottom(Minecraft mc) {
		BossHealthOverlay boss = mc.gui.hud.getBossOverlay();
		int count = bossBarCount(boss);
		if (count > 0) {
			return 19 * count + 2;
		}
		return (boss.shouldDarkenScreen() || boss.shouldCreateWorldFog()) ? 36 : 0;
	}

	private static int bossBarCount(BossHealthOverlay boss) {
		try {
			if (!bossReflectTried) {
				bossReflectTried = true;
				bossEvents = BossHealthOverlay.class.getDeclaredField("events");
				bossEvents.setAccessible(true);
			}
			if (bossEvents != null && bossEvents.get(boss) instanceof Map<?, ?> map) {
				return map.size();
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			bossEvents = null;
		}
		return 0;
	}
}
