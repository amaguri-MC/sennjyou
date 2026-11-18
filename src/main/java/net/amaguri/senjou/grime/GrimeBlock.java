package net.amaguri.senjou.grime;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 汚れブロック。ヒカリゴケ(MultifaceBlock)と同じく、空気マスに置かれて隣の固体ブロックの面に貼りつく。
 * 濃さ LEVEL(1〜4。4=隙間なくべったり) はブロック全体で1つ。拡散はしない（MultifaceSpreadeableBlock を継がない）。
 */
public class GrimeBlock extends MultifaceBlock {
	public static final MapCodec<GrimeBlock> CODEC = simpleCodec(GrimeBlock::new);
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, Grime.MAX_LEVEL);

	public GrimeBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<GrimeBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LEVEL);
	}
}
