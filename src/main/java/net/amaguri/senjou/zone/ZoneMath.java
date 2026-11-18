package net.amaguri.senjou.zone;

/**
 * Minecraft に依存しない計算だけを集めたクラス（javac 単体でテストできる）。
 * クリア率の丸めと、汚し方（むらのある値ノイズ）。
 */
public final class ZoneMath {
	private ZoneMath() {
	}

	/** 体積の上限（マス）。 */
	public static final long MAX_VOLUME = 300_000L;

	/** 面ごとに汚れを貼る確率の目安（約85%）。テストで確かめる。 */
	public static final double TARGET_COVER = 0.85;

	/**
	 * クリア率(%)。小数切り捨て。remaining が本当に0のときだけ100。initial が0以下なら100扱い。
	 */
	public static int percent(int initial, int remaining) {
		if (initial <= 0 || remaining <= 0) {
			return 100;
		}
		long done = (long) (initial - remaining) * 100L / initial;
		return (int) Math.max(0L, Math.min(99L, done));
	}

	/** 体積。オーバーフローしないよう long で返す。 */
	public static long volume(int sx, int sy, int sz) {
		return (long) sx * sy * sz;
	}

	/** チャンク列のキー。 */
	public static long columnKey(int chunkX, int chunkZ) {
		return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
	}

	public static int columnX(long key) {
		return (int) key;
	}

	public static int columnZ(long key) {
		return (int) (key >> 32);
	}

	// ---- むらのある汚れ ----

	// 面ごとの判定値 score = 0.62*ノイズ + 0.38*一様乱数 が COVER_CUT を超えたら貼る。
	// 値は ZoneMathTest の実測で約85%になるよう調整した。
	static final double COVER_CUT = 0.3427;
	// 濃さは別の低周波ノイズで決める（1:2:3 がおおむね均等になる境目）。
	static final double LEVEL_CUT_1 = 0.4247;
	static final double LEVEL_CUT_2 = 0.5373;

	/**
	 * マス(x,y,z)の face 側(0..5)に貼る汚れの濃さ。今は常に Grime.MAX_LEVEL（全面・隙間なし）。
	 * 同じ seed・座標なら必ず同じ値。
	 */
	public static int levelAt(long seed, int x, int y, int z, int face) {
		// 汚すときは全面を最大の濃さ（隙間なし）にする。むら版は mottledLevelAt。
		return net.amaguri.senjou.grime.Grime.MAX_LEVEL;
	}

	/** 旧仕様: むらのある汚れ（約85%・濃さ1〜3）。今は未使用。 */
	public static int mottledLevelAt(long seed, int x, int y, int z, int face) {
		double n = fbm(seed, x / 4.0, y / 4.0, z / 4.0);
		double h = hash01(seed ^ 0x51ED270BL, x, y * 6 + face, z);
		double score = 0.62 * n + 0.38 * h;
		if (score < COVER_CUT) {
			return 0;
		}
		double m = fbm(seed ^ 0x9E3779B97F4A7C15L, x / 3.0 + 31.7, y / 3.0 - 12.3, z / 3.0 + 7.1);
		if (m < LEVEL_CUT_1) {
			return 1;
		}
		if (m < LEVEL_CUT_2) {
			return 2;
		}
		return 3;
	}

	/** 2オクターブの値ノイズ。おおむね 0..1。 */
	static double fbm(long seed, double x, double y, double z) {
		double a = value3(seed, x, y, z);
		double b = value3(seed + 77, x * 2.1, y * 2.1, z * 2.1);
		return (a * 2.0 + b) / 3.0;
	}

	/** 3次元の値ノイズ（格子点の乱数を smoothstep で補間）。0..1。 */
	static double value3(long seed, double x, double y, double z) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int z0 = (int) Math.floor(z);
		double fx = smooth(x - x0);
		double fy = smooth(y - y0);
		double fz = smooth(z - z0);
		double c000 = hash01(seed, x0, y0, z0);
		double c100 = hash01(seed, x0 + 1, y0, z0);
		double c010 = hash01(seed, x0, y0 + 1, z0);
		double c110 = hash01(seed, x0 + 1, y0 + 1, z0);
		double c001 = hash01(seed, x0, y0, z0 + 1);
		double c101 = hash01(seed, x0 + 1, y0, z0 + 1);
		double c011 = hash01(seed, x0, y0 + 1, z0 + 1);
		double c111 = hash01(seed, x0 + 1, y0 + 1, z0 + 1);
		double x00 = c000 + (c100 - c000) * fx;
		double x10 = c010 + (c110 - c010) * fx;
		double x01 = c001 + (c101 - c001) * fx;
		double x11 = c011 + (c111 - c011) * fx;
		double y0v = x00 + (x10 - x00) * fy;
		double y1v = x01 + (x11 - x01) * fy;
		return y0v + (y1v - y0v) * fz;
	}

	private static double smooth(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	/** 座標のハッシュ → 0以上1未満。 */
	static double hash01(long seed, int x, int y, int z) {
		long h = seed;
		h ^= x * 0x632BE59BD9B4E019L;
		h = mix(h);
		h ^= y * 0x9E3779B97F4A7C15L;
		h = mix(h);
		h ^= z * 0xC2B2AE3D27D4EB4FL;
		h = mix(h);
		return (h >>> 11) * 0x1.0p-53;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
