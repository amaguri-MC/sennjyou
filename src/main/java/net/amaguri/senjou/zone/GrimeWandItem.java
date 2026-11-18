package net.amaguri.senjou.zone;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 汚しの杖。左クリック=地点1（ZoneSetup の AttackBlockCallback）、右クリック=地点2、スニーク+右クリック=選択範囲を汚して区域を作る。
 */
public class GrimeWandItem extends Item {
	public GrimeWandItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (!context.getLevel().isClientSide() && player instanceof ServerPlayer sp) {
			if (sp.isShiftKeyDown()) {
				WandSelection.create(sp);
			} else {
				WandSelection.setPos2(sp, context.getClickedPos());
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** 空中での右クリック: スニーク中なら作成、そうでなければ案内。 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			if (sp.isShiftKeyDown()) {
				WandSelection.create(sp);
			} else {
				WandSelection.hint(sp);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
