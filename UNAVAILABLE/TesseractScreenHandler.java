package cat.lacycat.tesseracts;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class TesseractScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final TesseractBlockEntity tesseract;

    // 클라이언트용 생성자
    public TesseractScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(9), new ArrayPropertyDelegate(4), null);
    }

    // 서버용 생성자
    public TesseractScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                  PropertyDelegate propertyDelegate, TesseractBlockEntity tesseract) {
        super(TesseractMod.TESSERACT_SCREEN_HANDLER, syncId);

        checkSize(inventory, 9);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.tesseract = tesseract;

        inventory.onOpen(playerInventory.player);
        this.addProperties(propertyDelegate);

        // 테서랙트 인벤토리 슬롯 (3x3)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                this.addSlot(new TesseractSlot(inventory, j + i * 3, 8 + j * 18, 84 + i * 18));
            }
        }

        // 플레이어 인벤토리
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 140 + i * 18));
            }
        }

        // 플레이어 핫바
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 198));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (index < 9) {
                // 테서랙트 슬롯에서 플레이어 인벤토리로
                if (!this.insertItem(originalStack, 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 플레이어 인벤토리에서 테서랙트 슬롯으로
                if (!this.insertItem(originalStack, 0, 9, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return newStack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }

    // 테서랙트 정보 가져오기
    public TesseractBlockEntity getTesseract() {
        return tesseract;
    }

    public long getStoredEnergy() {
        return tesseract != null ? tesseract.getStoredEnergy() : 0;
    }

    public long getMaxEnergy() {
        return tesseract != null ? tesseract.getMaxEnergy() : 0;
    }

    public TesseractBlockEntity.TesseractMode getMode() {
        return tesseract != null ? tesseract.getMode() : TesseractBlockEntity.TesseractMode.SEND_RECEIVE;
    }

    // 서버 액션들 (패킷으로 처리해야 함)
    public void setNetworkName(String networkName) {
        if (tesseract != null && !tesseract.getWorld().isClient) {
            tesseract.joinNetwork(networkName);
        }
    }

    public void setTesseractName(String tesseractName) {
        if (tesseract != null && !tesseract.getWorld().isClient) {
            tesseract.setTesseractName(tesseractName);
        }
    }

    public void cycleTesseractMode() {
        if (tesseract != null && !tesseract.getWorld().isClient) {
            TesseractBlockEntity.TesseractMode currentMode = tesseract.getMode();
            TesseractBlockEntity.TesseractMode nextMode = switch (currentMode) {
                case SEND_RECEIVE -> TesseractBlockEntity.TesseractMode.SEND_ONLY;
                case SEND_ONLY -> TesseractBlockEntity.TesseractMode.RECEIVE_ONLY;
                case RECEIVE_ONLY -> TesseractBlockEntity.TesseractMode.SEND_RECEIVE;
            };
            tesseract.setMode(nextMode);
        }
    }

    // 테서랙트 전용 슬롯
    private static class TesseractSlot extends Slot {
        public TesseractSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            // 테서랙트 자체는 들어갈 수 없음 (무한 루프 방지)
            return !(stack.getItem() instanceof BlockItem blockItem &&
                    blockItem.getBlock() instanceof TesseractBlock);
        }

        @Override
        public int getMaxItemCount() {
            return 64;
        }
    }

    // PropertyDelegate 구현 (에너지 동기화용)
    private static class ArrayPropertyDelegate implements PropertyDelegate {
        private final int[] values;

        public ArrayPropertyDelegate(int size) {
            this.values = new int[size];
        }

        @Override
        public int get(int index) {
            return values[index];
        }

        @Override
        public void set(int index, int value) {
            values[index] = value;
        }

        @Override
        public int size() {
            return values.length;
        }
    }

    // 간단한 인벤토리 구현
    private static class SimpleInventory implements Inventory {
        private final ItemStack[] items;

        public SimpleInventory(int size) {
            this.items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                this.items[i] = ItemStack.EMPTY;
            }
        }

        @Override
        public int size() {
            return items.length;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getStack(int slot) {
            return slot >= 0 && slot < items.length ? items[slot] : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            if (slot >= 0 && slot < items.length && !items[slot].isEmpty()) {
                ItemStack stack = items[slot].split(amount);
                if (items[slot].isEmpty()) {
                    items[slot] = ItemStack.EMPTY;
                }
                markDirty();
                return stack;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot) {
            if (slot >= 0 && slot < items.length) {
                ItemStack stack = items[slot];
                items[slot] = ItemStack.EMPTY;
                markDirty();
                return stack;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (slot >= 0 && slot < items.length) {
                items[slot] = stack;
                markDirty();
            }
        }

        @Override
        public void markDirty() {
            // 클라이언트에서는 비어있음
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void clear() {
            for (int i = 0; i < items.length; i++) {
                items[i] = ItemStack.EMPTY;
            }
            markDirty();
        }
    }
}