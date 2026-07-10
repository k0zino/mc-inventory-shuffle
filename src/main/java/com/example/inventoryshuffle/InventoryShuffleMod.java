package com.example.inventoryshuffle;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventoryShuffleMod implements ModInitializer {
    private static final int MAIN_INVENTORY_SIZE = 36; // 0-8 hotbar, 9-35 backpack
    private static final int DEFAULT_INTERVAL_TICKS = 20 * 10;

    // true = empty slots also participate in random shuffle
    // false = only shuffle non-empty items, empty slots stay in place
    private static final boolean SHUFFLE_EMPTY_SLOTS = true;

    private static boolean enabled = false;
    private static int intervalTicks = DEFAULT_INTERVAL_TICKS;
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        registerCommands();
        registerTickEvent();
    }

    private void registerTickEvent() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!enabled) {
                return;
            }

            tickCounter++;

            if (tickCounter < intervalTicks) {
                return;
            }

            tickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                shuffleMainInventory(player);
            }
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("invshuffle")
                    .requires(source -> source.permissions() instanceof LevelBasedPermissionSet levelPermissions
                            && levelPermissions.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS))

                    .then(Commands.literal("on")
                            .executes(context -> {
                                enabled = true;
                                tickCounter = 0;
                                context.getSource().sendSuccess(
                                        () -> Component.literal("Inventory Shuffle 已开启。"),
                                        true
                                );
                                return 1;
                            })
                    )

                    .then(Commands.literal("off")
                            .executes(context -> {
                                enabled = false;
                                tickCounter = 0;
                                context.getSource().sendSuccess(
                                        () -> Component.literal("Inventory Shuffle 已关闭。"),
                                        true
                                );
                                return 1;
                            })
                    )

                    .then(Commands.literal("status")
                            .executes(context -> {
                                int seconds = intervalTicks / 20;
                                String status = enabled ? "开启" : "关闭";
                                context.getSource().sendSuccess(
                                        () -> Component.literal("Inventory Shuffle 当前状态：" + status + "，间隔：" + seconds + " 秒。"),
                                        false
                                );
                                return 1;
                            })
                    )

                    .then(Commands.literal("interval")
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                    .executes(context -> {
                                        int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                        intervalTicks = seconds * 20;
                                        tickCounter = 0;

                                        context.getSource().sendSuccess(
                                                () -> Component.literal("Inventory Shuffle 间隔已设置为 " + seconds + " 秒。"),
                                                true
                                        );
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    private static void shuffleMainInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();

        if (SHUFFLE_EMPTY_SLOTS) {
            shuffleAllSlots(inventory);
        } else {
            shuffleOnlyNonEmptySlots(inventory);
        }

        inventory.setChanged();

        // Sync to client so the player's inventory screen updates immediately
        player.inventoryMenu.broadcastChanges();
    }

    private static void shuffleAllSlots(Inventory inventory) {
        List<ItemStack> stacks = new ArrayList<>(MAIN_INVENTORY_SIZE);

        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            stacks.add(inventory.getItem(slot));
        }

        Collections.shuffle(stacks);

        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, stacks.get(slot));
        }
    }

    private static void shuffleOnlyNonEmptySlots(Inventory inventory) {
        List<Integer> occupiedSlots = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();

        for (int slot = 0; slot < MAIN_INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);

            if (!stack.isEmpty()) {
                occupiedSlots.add(slot);
                stacks.add(stack);
            }
        }

        Collections.shuffle(stacks);

        for (int i = 0; i < occupiedSlots.size(); i++) {
            inventory.setItem(occupiedSlots.get(i), stacks.get(i));
        }
    }
}
