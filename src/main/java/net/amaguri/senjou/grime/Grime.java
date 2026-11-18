package net.amaguri.senjou.grime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 汚れの共通API。CONTRACT FILE: シグネチャは司令塔が決めた。中身(本体)は W1 が書く。W2/W3 はこのAPIだけを使う。
 *
 * 用語:
 *  - cell: 汚れブロック(senjou:grime)が置かれる空気マスの座標。
 *  - face: そのマスのどの側に貼るか。cell.relative(face) が支えの固体ブロック（その face.getOpposite() 面が丈夫）。
 *  - 汚れ量(amount): LEVEL(1〜3) × 貼られている面の数。クリア率の単位。
 */
public final class Grime {
	private Grime() {
	}

	public static final int MAX_LEVEL = 3;

	/** state が senjou:grime なら true。 */
	public static boolean isGrime(BlockState state) {
		throw new UnsupportedOperationException("W1");
	}

	/** 汚れ量。汚れでなければ 0。 */
	public static int amount(BlockState state) {
		throw new UnsupportedOperationException("W1");
	}

	/** cell に face 側の汚れを貼れるか（cell が空気/水/既存の汚れで、支えの面が丈夫）。 */
	public static boolean canPlace(ServerLevel level, BlockPos cell, Direction face) {
		throw new UnsupportedOperationException("W1");
	}

	/**
	 * cell の face 側に濃さ lvl(1〜3) の汚れを貼る。既に汚れがあれば面を足し、濃さは大きい方。
	 * 隣への更新は出さない（大量に置くため。setBlock のフラグ 2）。貼れなければ false。
	 */
	public static boolean place(ServerLevel level, BlockPos cell, Direction face, int lvl) {
		throw new UnsupportedOperationException("W1");
	}

	/**
	 * cell の汚れを1段薄くする。LEVEL 1 なら消す（水に浸かっていたら水源に、それ以外は空気に）。
	 * 戻り値: 減った汚れ量（= 面の数）。汚れが無ければ 0。
	 */
	public static int reduce(ServerLevel level, BlockPos cell) {
		throw new UnsupportedOperationException("W1");
	}

	/** cell の汚れを全部消す（コマンドの掃除・区域削除用）。戻り値: 消えた汚れ量。 */
	public static int remove(ServerLevel level, BlockPos cell) {
		throw new UnsupportedOperationException("W1");
	}
}
