package com.adminpro.gui;

import com.adminpro.util.ItemUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiFunction;

public class AdminScreenHandler extends ScreenHandler {
    private final SimpleInventory inventory;

    private AdminScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, int rows) {
        super(getType(rows), syncId);
        this.inventory = inventory;

        int containerSlots = rows * 9;

        for (int i = 0; i < containerSlots; i++) {
            addSlot(new Slot(inventory, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        }

        int playerInvStart = 18 + rows * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, playerInvStart + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, playerInvStart + 58));
        }
    }

    public static AdminScreenHandler create(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, int rows) {
        return new AdminScreenHandler(syncId, playerInventory, inventory, rows);
    }

    private static ScreenHandlerType<?> getType(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            case 6 -> ScreenHandlerType.GENERIC_9X6;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        if (slot >= 0 && slot < inventory.size()) {
            ItemStack stack = getSlot(slot).getStack();
            if (ItemUtil.isBorderOrFiller(stack)) return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < inventory.size()) {
            ItemStack stack = getSlot(slotIndex).getStack();
            if (ItemUtil.isBorderOrFiller(stack)) return;
            if (player instanceof ServerPlayerEntity sp) {
                var session = GuiManager.getSession(sp);
                if (session != null && session.handler != null && session.handler.apply(sp, slotIndex)) {
                    return;
                }
            }
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    public SimpleInventory getInventory() {
        return inventory;
    }
}
