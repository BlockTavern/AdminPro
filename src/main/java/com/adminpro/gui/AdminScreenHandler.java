package com.adminpro.gui;

import com.adminpro.util.ItemUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
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
    private ServerPlayerEntity targetPlayer;

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

    public void setTargetPlayer(ServerPlayerEntity target) {
        this.targetPlayer = target;
        rebuildSyncSlots();
    }

    private void rebuildSyncSlots() {
        if (targetPlayer == null) return;
        int containerSize = inventory.size();
        for (int i = 0; i < containerSize; i++) {
            Slot old = slots.get(i);
            if (old.getClass() == Slot.class) {
                SyncSlot ss = new SyncSlot(inventory, i, old.x, old.y);
                ss.targetInv = targetPlayer.getInventory();
                ss.targetSlot = mapToTargetSlot(i);
                slots.set(i, ss);
            }
        }
    }

    private int mapToTargetSlot(int guiSlot) {
        int containerSize = inventory.size();
        int rows = containerSize / 9;
        if (rows != 6) return -1;

        if (guiSlot >= 0 && guiSlot <= 26) return 9 + guiSlot;

        if (guiSlot >= 27 && guiSlot <= 35) return guiSlot - 27;

        if (guiSlot == 36) return 36 + 3;
        if (guiSlot == 37) return 36 + 2;
        if (guiSlot == 38) return 36 + 1;
        if (guiSlot == 39) return 36;

        if (guiSlot == 44) return 999;

        return -1;
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
                if (session != null && session.handler != null) {
                    session.currentButton = button;
                    if (session.handler.apply(sp, slotIndex)) {
                        return;
                    }
                }
            }
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity sp) {
            var session = GuiManager.getSession(sp);
            if (session != null && session.onClose != null) {
                session.onClose.run();
                session.onClose = null;
            }
        }
        if (targetPlayer != null && targetPlayer.isAlive()) {
            syncInventoryToTarget();
        }
        super.onClosed(player);
    }

    private void syncInventoryToTarget() {
        PlayerInventory targetInv = targetPlayer.getInventory();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int guiSlot = row * 9 + col;
                int mcSlot = 9 + row * 9 + col;
                targetInv.setStack(mcSlot, inventory.getStack(guiSlot).copy());
            }
        }

        for (int col = 0; col < 9; col++) {
            int guiSlot = 27 + col;
            int mcSlot = col;
            targetInv.setStack(mcSlot, inventory.getStack(guiSlot).copy());
        }

        targetInv.setStack(36 + 3, inventory.getStack(36).copy());
        targetInv.setStack(36 + 2, inventory.getStack(37).copy());
        targetInv.setStack(36 + 1, inventory.getStack(38).copy());
        targetInv.setStack(36, inventory.getStack(39).copy());

        targetPlayer.setStackInHand(net.minecraft.util.Hand.OFF_HAND, inventory.getStack(44).copy());
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    public SimpleInventory getInventory() {
        return inventory;
    }

    private static class SyncSlot extends Slot {
        PlayerInventory targetInv;
        int targetSlot = -1;
        private boolean syncing = false;

        SyncSlot(Inventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public void setStack(ItemStack newStack) {
            super.setStack(newStack);
            syncToTarget();
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            super.onTakeItem(player, stack);
            syncToTarget();
        }

        private void syncToTarget() {
            if (syncing || targetInv == null || targetSlot < 0) return;
            syncing = true;
            try {
                ItemStack current = getStack().copy();
                if (targetSlot == 999) {
                    targetInv.player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, current);
                } else {
                    targetInv.setStack(targetSlot, current);
                }
            } finally {
                syncing = false;
            }
        }
    }
}
