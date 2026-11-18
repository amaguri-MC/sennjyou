package net.amaguri.senjou.grime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
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
		return state.getBlock() instanceof GrimeBlock;
	}

	/** 汚れ量。汚れでなければ 0。 */
	public static int amount(BlockState state) {
		if (!isGrime(state)) {
			return 0;
		}
		return state.getValue(GrimeBlock.LEVEL) * MultifaceBlock.availableFaces(state).size();
	}

	/** cell に face 側の汚れを貼れるか（cell が空気/水/既存の汚れで、支えの面が丈夫）。 */
	public static boolean canPlace(ServerLevel level, BlockPos cell, Direction face) {
		BlockState state = level.getBlockState(cell);
		boolean cellOk = state.isAir() || isGrime(state) || isWaterSource(state);
		return cellOk && MultifaceBlock.canAttachTo(level, cell, face);
	}

	/**
	 * cell の face 側に濃さ lvl(1〜3) の汚れを貼る。既に汚れがあれば面を足し、濃さは大きい方。
	 * 隣への更新は出さない（大量に置くため。setBlock のフラグ 2）。貼れなければ false。
	 */
	public static boolean place(ServerLevel level, BlockPos cell, Direction face, int lvl) {
		if (!canPlace(level, cell, face)) {
			return false;
		}
		int clamped = Math.max(1, Math.min(MAX_LEVEL, lvl));
		BlockState current = level.getBlockState(cell);
		BlockState next;
		if (isGrime(current)) {
			next = current
					.setValue(MultifaceBlock.getFaceProperty(face), true)
					.setValue(GrimeBlock.LEVEL, Math.max(current.getValue(GrimeBlock.LEVEL), clamped));
		} else {
			next = GrimeSetup.GRIME.defaultBlockState()
					.setValue(MultifaceBlock.getFaceProperty(face), true)
					.setValue(GrimeBlock.LEVEL, clamped)
					.setValue(MultifaceBlock.WATERLOGGED, isWaterSource(current));
		}
		if (next != current) {
			level.setBlock(cell, next, Block.UPDATE_CLIENTS);
		}
		return true;
	}

	/**
	 * cell の汚れを1段薄くする。LEVEL 1 なら消す（水に浸かっていたら水源に、それ以外は空気に）。
	 * 戻り値: 減った汚れ量（= 面の数）。汚れが無ければ 0。
	 */
	public static int reduce(ServerLevel level, BlockPos cell) {
		BlockState state = level.getBlockState(cell);
		if (!isGrime(state)) {
			return 0;
		}
		int faces = MultifaceBlock.availableFaces(state).size();
		int lvl = state.getValue(GrimeBlock.LEVEL);
		if (lvl > 1) {
			level.setBlock(cell, state.setValue(GrimeBlock.LEVEL, lvl - 1), Block.UPDATE_CLIENTS);
		} else {
			level.setBlock(cell, emptied(state), Block.UPDATE_ALL);
		}
		return faces;
	}

	/** cell の汚れを全部消す（コマンドの掃除・区域削除用）。戻り値: 消えた汚れ量。 */
	public static int remove(ServerLevel level, BlockPos cell) {
		BlockState state = level.getBlockState(cell);
		if (!isGrime(state)) {
			return 0;
		}
		int removed = amount(state);
		level.setBlock(cell, emptied(state), Block.UPDATE_ALL);
		return removed;
	}

	/** 汚れが消えた後のマス（水に浸かっていたら水源、それ以外は空気）。 */
	private static BlockState emptied(BlockState grime) {
		return grime.getValue(MultifaceBlock.WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
	}

	private static boolean isWaterSource(BlockState state) {
		return state.is(Blocks.WATER) && state.getFluidState().isSource();
	}
}
