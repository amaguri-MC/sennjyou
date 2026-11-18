package net.amaguri.senjou.zone;

import java.util.UUID;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /senjou (権限2)
 *   dirty &lt;from&gt; &lt;to&gt; [name] / zones / remove &lt;name&gt; / reset &lt;name&gt; / clean &lt;name&gt;
 * 結果は実行者にだけ返す。
 */
public final class ZoneCommands {
	private ZoneCommands() {
	}

	private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(ZoneData.get(ctx.getSource().getServer()).zones.stream().map(z -> z.name).collect(Collectors.toList()), builder);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("senjou")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("dirty")
						.then(Commands.argument("from", BlockPosArgument.blockPos())
								.then(Commands.argument("to", BlockPosArgument.blockPos())
										.executes(ctx -> dirty(ctx, null))
										.then(Commands.argument("name", StringArgumentType.greedyString())
												.executes(ctx -> dirty(ctx, StringArgumentType.getString(ctx, "name")))))))
				.then(Commands.literal("zones").executes(ZoneCommands::zones))
				.then(Commands.literal("remove")
						.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(ZONE_NAMES)
								.executes(ctx -> byName(ctx, Action.REMOVE))))
				.then(Commands.literal("reset")
						.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(ZONE_NAMES)
								.executes(ctx -> byName(ctx, Action.RESET))))
				.then(Commands.literal("clean")
						.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(ZONE_NAMES)
								.executes(ctx -> byName(ctx, Action.CLEAN)))));
	}

	private enum Action {
		REMOVE, RESET, CLEAN
	}

	private static UUID requester(CommandSourceStack src) {
		ServerPlayer p = src.getPlayer();
		return p == null ? null : p.getUUID();
	}

	private static int dirty(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
		CommandSourceStack src = ctx.getSource();
		BlockPos from = BlockPosArgument.getBlockPos(ctx, "from");
		BlockPos to = BlockPosArgument.getBlockPos(ctx, "to");
		Component err = ZoneManager.startDirty(src.getServer(), src.getLevel(), from, to, name, requester(src));
		if (err != null) {
			src.sendFailure(err);
			return 0;
		}
		src.sendSuccess(() -> Component.translatable("senjou.cmd.dirty_started"), false);
		return 1;
	}

	private static int zones(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		ZoneData data = ZoneData.get(src.getServer());
		if (data.zones.isEmpty()) {
			src.sendSuccess(() -> Component.translatable("senjou.cmd.zones_empty"), false);
			return 0;
		}
		src.sendSuccess(() -> Component.translatable("senjou.cmd.zones_header", data.zones.size()), false);
		for (CleanZone z : data.zones) {
			String shortDim = z.dimension.startsWith("minecraft:") ? z.dimension.substring("minecraft:".length()) : z.dimension;
			Component line = z.busy
					? Component.translatable("senjou.cmd.zones_entry_busy", z.name, shortDim)
					: Component.translatable("senjou.cmd.zones_entry", z.name, z.percent(), z.remaining, z.initial, shortDim,
							z.min.getX(), z.min.getY(), z.min.getZ(), z.max.getX(), z.max.getY(), z.max.getZ());
			src.sendSuccess(() -> line, false);
		}
		return data.zones.size();
	}

	private static int byName(CommandContext<CommandSourceStack> ctx, Action action) {
		CommandSourceStack src = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name").strip();
		ZoneData data = ZoneData.get(src.getServer());
		CleanZone zone = data.find(name);
		if (zone == null) {
			src.sendFailure(Component.translatable("senjou.zone.not_found", name));
			return 0;
		}
		Component err = switch (action) {
			case REMOVE -> ZoneManager.remove(src.getServer(), zone, requester(src));
			case RESET -> ZoneManager.reset(src.getServer(), zone, requester(src));
			case CLEAN -> ZoneManager.clean(src.getServer(), zone, requester(src));
		};
		if (err != null) {
			src.sendFailure(err);
			return 0;
		}
		String key = switch (action) {
			case REMOVE -> "senjou.cmd.remove_started";
			case RESET -> "senjou.cmd.reset_started";
			case CLEAN -> "senjou.cmd.clean_started";
		};
		src.sendSuccess(() -> Component.translatable(key, name), false);
		return 1;
	}
}
