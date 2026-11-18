package net.amaguri.senjou.washer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** プレイヤーごと・マスごとの「当たった量」。しばらく当たらなければ忘れる。サーバースレッド専用。 */
final class SprayState {
	private SprayState() {
	}

	/** この tick 数当たらなければ忘れる。 */
	static final int FORGET_TICKS = 30;

	private static final class Cell {
		float amount;
		int lastTick;
	}

	private static final Map<UUID, Map<Long, Cell>> PLAYERS = new HashMap<>();

	/** cell(pos.asLong()) に weight を足し、合計を返す。 */
	static float add(UUID player, long cellKey, float weight, int nowTick) {
		Map<Long, Cell> cells = PLAYERS.computeIfAbsent(player, k -> new HashMap<>());
		Cell c = cells.get(cellKey);
		if (c == null || nowTick - c.lastTick > FORGET_TICKS) {
			c = new Cell();
			cells.put(cellKey, c);
		}
		c.amount += weight;
		c.lastTick = nowTick;
		return c.amount;
	}

	/** しきい値ぶんを引く（余りは残す）。 */
	static void consume(UUID player, long cellKey, float threshold) {
		Map<Long, Cell> cells = PLAYERS.get(player);
		if (cells == null) {
			return;
		}
		Cell c = cells.get(cellKey);
		if (c != null) {
			c.amount = Math.max(0f, c.amount - threshold);
		}
	}

	static void forget(UUID player, long cellKey) {
		Map<Long, Cell> cells = PLAYERS.get(player);
		if (cells != null) {
			cells.remove(cellKey);
		}
	}

	/** 古い貯まり量を捨てる。END_SERVER_TICK から呼ぶ。 */
	static void sweep(int nowTick) {
		if (PLAYERS.isEmpty() || nowTick % 20 != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Map<Long, Cell>>> pit = PLAYERS.entrySet().iterator();
		while (pit.hasNext()) {
			Map<Long, Cell> cells = pit.next().getValue();
			cells.values().removeIf(c -> nowTick - c.lastTick > FORGET_TICKS);
			if (cells.isEmpty()) {
				pit.remove();
			}
		}
	}
}
