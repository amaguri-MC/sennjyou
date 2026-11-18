package net.amaguri.senjou.washer;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.amaguri.senjou.grime.Grime;

/** 高圧洗浄機。右クリック長押し中、毎tick サーバー側で水を当てて汚れを薄くする。 */
public class PressureWasherItem extends Item {
	/** 射程（ブロック）。 */
	private static final double RANGE = 8.0;
	/** ノズルの広がり半径。 */
	private static final double SPREAD = 1.2;
	/** 中心で1段減らすのに必要な量（≒tick数）。LEVEL3 が約1秒で消える。 */
	private static final float THRESHOLD = 6.0f;
	/** 泥色。 */
	private static final int MUD_COLOR = 0x6B5A3E;

	public PressureWasherItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BRUSH;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
		if (!(level instanceof ServerLevel serverLevel) || !(user instanceof ServerPlayer player)) {
			return;
		}
		int now = serverLevel.getServer().getTickCount();
		RandomSource random = serverLevel.getRandom();

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(RANGE));
		BlockHitResult hit = serverLevel.clip(
				new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		boolean hitBlock = hit.getType() == HitResult.Type.BLOCK;
		Vec3 target = hitBlock ? hit.getLocation() : end;

		// ノズル位置（目の少し前・下・利き手側）。
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0E-4 ? Vec3.ZERO : right.normalize();
		double side = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? 0.25 : -0.25;
		Vec3 muzzle = eye.add(look.scale(0.9)).add(right.scale(side)).add(0, -0.28, 0);

		// 水しぶきの線。
		Vec3 beam = target.subtract(muzzle);
		double len = beam.length();
		if (len > 0.5) {
			Vec3 step = beam.scale(1.0 / len);
			for (double d = 0.3; d < len; d += 0.7) {
				Vec3 p = muzzle.add(step.scale(d));
				serverLevel.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.0);
			}
		}
		serverLevel.sendParticles(ParticleTypes.SPLASH, muzzle.x, muzzle.y, muzzle.z, 2, 0.05, 0.05, 0.05, 0.02);

		// シャー音（数tickおき・小さめ）。
		if (now % 3 == 0) {
			Vec3 at = hitBlock ? target : muzzle;
			serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.WEATHER_RAIN, SoundSource.PLAYERS,
					0.16f, 1.5f + random.nextFloat() * 0.3f);
		}

		if (hitBlock) {
			serverLevel.sendParticles(ParticleTypes.SPLASH, target.x, target.y, target.z, 6, 0.18, 0.12, 0.18, 0.12);
			washBlocks(serverLevel, player, hit, target, now, random);
		}
		washEntities(serverLevel, player, muzzle, target, look, random);
	}

	private static void washBlocks(ServerLevel level, ServerPlayer player, BlockHitResult hit, Vec3 target, int now,
			RandomSource random) {
		// 当たったマスが汚れでなければ、その手前（当たった面側）のマスも調べる。
		BlockPos hitPos = hit.getBlockPos();
		BlockPos front = hitPos.relative(hit.getDirection());

		int x0 = floor(target.x - SPREAD);
		int x1 = floor(target.x + SPREAD);
		int y0 = floor(target.y - SPREAD);
		int y1 = floor(target.y + SPREAD);
		int z0 = floor(target.z - SPREAD);
		int z1 = floor(target.z + SPREAD);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					boolean isHitCell = x == hitPos.getX() && y == hitPos.getY() && z == hitPos.getZ();
					boolean isFrontCell = x == front.getX() && y == front.getY() && z == front.getZ();
					double d = distanceToCell(target, x, y, z);
					if (state.getBlock() instanceof BaseFireBlock) {
						if (d <= SPREAD) {
							level.removeBlock(cursor, false);
							level.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.FIRE_EXTINGUISH,
									SoundSource.BLOCKS, 0.5f, 1.8f);
							level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y + 0.4, z + 0.5, 4, 0.2, 0.2, 0.2,
									0.02);
						}
						continue;
					}
					if (!Grime.isGrime(state)) {
						continue;
					}
					// 直撃マス・当たり面の手前マスは必ず対象（広がり半径の外でも）。それ以外は半径内だけ。
					if (!isHitCell && !isFrontCell && d > SPREAD) {
						continue;
					}
					float weight = (isHitCell || isFrontCell) ? 1.0f : (float) Math.max(0.15, 1.0 - d / SPREAD);
					washCell(level, player, cursor.immutable(), state, weight, now, random);
				}
			}
		}
	}

	private static void washCell(ServerLevel level, ServerPlayer player, BlockPos cell, BlockState before, float weight,
			int now, RandomSource random) {
		long key = cell.asLong();
		float total = SprayState.add(player.getUUID(), key, weight, now);
		if (total < THRESHOLD) {
			return;
		}
		int reduced = Grime.reduce(level, cell);
		if (reduced <= 0) {
			SprayState.forget(player.getUUID(), key);
			return;
		}
		SprayState.consume(player.getUUID(), key, THRESHOLD);

		double cx = cell.getX() + 0.5;
		double cy = cell.getY() + 0.5;
		double cz = cell.getZ() + 0.5;
		level.sendParticles(new DustParticleOptions(MUD_COLOR, 1.1f), cx, cy, cz, 8, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, before), cx, cy, cz, 6, 0.3, 0.3, 0.3, 0.05);

		if (!Grime.isGrime(level.getBlockState(cell))) {
			// 消えた。
			SprayState.forget(player.getUUID(), key);
			level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.22f,
					1.5f + random.nextFloat() * 0.3f);
		}
	}

	private static void washEntities(ServerLevel level, ServerPlayer player, Vec3 muzzle, Vec3 target, Vec3 look,
			RandomSource random) {
		AABB box = new AABB(muzzle, target).inflate(0.7);
		List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !e.isSpectator());
		for (LivingEntity e : list) {
			Vec3 mid = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
			if (distanceToSegment(mid, muzzle, target) > 0.5 + e.getBbWidth() * 0.5) {
				continue;
			}
			if (e.isOnFire()) {
				e.clearFire();
				level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f,
						1.6f);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, mid.x, mid.y, mid.z, 4, 0.2, 0.3, 0.2, 0.02);
			}
			// ダメージなしで少し押す。
			e.push(look.x * 0.05, 0.0, look.z * 0.05);
			e.hurtMarked = true;
			level.sendParticles(ParticleTypes.SPLASH, mid.x, mid.y, mid.z, 3, 0.2, 0.3, 0.2, 0.1);
		}
	}

	private static int floor(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}

	/** 点 p からマス (x,y,z) の立方体までの最短距離（p が中なら 0）。 */
	private static double distanceToCell(Vec3 p, int x, int y, int z) {
		double dx = Math.max(Math.max(x - p.x, 0), p.x - (x + 1));
		double dy = Math.max(Math.max(y - p.y, 0), p.y - (y + 1));
		double dz = Math.max(Math.max(z - p.z, 0), p.z - (z + 1));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double l2 = ab.lengthSqr();
		double t = l2 < 1.0E-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / l2));
		return p.distanceTo(a.add(ab.scale(t)));
	}
}
