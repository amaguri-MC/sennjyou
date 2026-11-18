package net.amaguri.senjou.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.amaguri.senjou.SenjouMod;

/**
 * Server -> client: 近くの掃除区域のクリア率。CONTRACT FILE (owner: 司令塔)。
 * W2 がプレイヤーごとに10tickおきに送る。区域から離れたら active=false を1回送る（HUDを消す）。
 * initial: 汚した直後の汚れ量（単位は Grime.amount の合計）。remaining: 今の残量。
 * クリア率 = (initial - remaining) / initial。initial==0 のときは100%扱い。
 * name: 区域名（表示用。空でもよい）。
 */
public record ZoneProgressPayload(boolean active, String name, int initial, int remaining) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ZoneProgressPayload> TYPE = new CustomPacketPayload.Type<>(SenjouMod.id("zone_progress"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ZoneProgressPayload> CODEC = StreamCodec.ofMember(ZoneProgressPayload::write, ZoneProgressPayload::read);

	public static final ZoneProgressPayload INACTIVE = new ZoneProgressPayload(false, "", 0, 0);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(active);
		buf.writeUtf(name);
		buf.writeVarInt(initial);
		buf.writeVarInt(remaining);
	}

	private static ZoneProgressPayload read(RegistryFriendlyByteBuf buf) {
		return new ZoneProgressPayload(buf.readBoolean(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
	}

	/** 0.0〜1.0 */
	public double cleared() {
		if (initial <= 0) {
			return 1.0;
		}
		return Math.max(0.0, Math.min(1.0, (initial - remaining) / (double) initial));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
