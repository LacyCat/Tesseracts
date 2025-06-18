package cat.lacycat.tesseracts;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TesseractNetwork {
    private final UUID networkId;
    private final String networkName;
    private final Set<TesseractBlockEntity> tesseracts = ConcurrentHashMap.newKeySet();
    private volatile long totalStoredEnergy = 0;
    private final Object energyLock = new Object();

    public TesseractNetwork(UUID networkId, String networkName) {
        this.networkId = networkId;
        this.networkName = networkName;
    }

    public void addTesseract(TesseractBlockEntity tesseract) {
        if (tesseract != null && tesseracts.add(tesseract)) {
            updateTotalEnergy();
        }
    }

    public void removeTesseract(TesseractBlockEntity tesseract) {
        if (tesseract != null && tesseracts.remove(tesseract)) {
            updateTotalEnergy();
        }
    }

    private void updateTotalEnergy() {
        synchronized (energyLock) {
            totalStoredEnergy = tesseracts.stream()
                    .filter(Objects::nonNull) // null 체크 추가
                    .filter(t -> !t.isRemoved()) // 제거된 블록 엔티티 필터링
                    .mapToLong(TesseractBlockEntity::getStoredEnergy)
                    .sum();
        }
    }

    /**
     * 네트워크에 에너지를 분배합니다
     * @param sender 에너지를 보내는 테서랙트
     * @param amount 보낼 에너지 양
     * @return 실제로 분배된 에너지 양
     */
    public long distributeEnergy(TesseractBlockEntity sender, long amount) {
        if (amount <= 0 || sender == null) return 0;

        List<TesseractBlockEntity> receivers = tesseracts.stream()
                .filter(Objects::nonNull)
                .filter(t -> !t.isRemoved())
                .filter(t -> t != sender)
                .filter(t -> canReceiveEnergy(t))
                .filter(t -> t.getStoredEnergy() < t.getMaxEnergy())
                .sorted(Comparator.comparingDouble(t -> t.getStoredEnergy() / (double)t.getMaxEnergy()))
                .toList();

        if (receivers.isEmpty()) return 0;

        long totalDistributed = 0;
        long remainingAmount = amount;

        // 에너지가 적은 테서랙트부터 우선적으로 충전
        for (TesseractBlockEntity receiver : receivers) {
            if (remainingAmount <= 0) break;

            long canReceive = receiver.getMaxEnergy() - receiver.getStoredEnergy();
            long toTransfer = Math.min(remainingAmount, canReceive);

            if (toTransfer > 0) {
                long actualTransferred = receiver.insertEnergy(toTransfer, false);
                totalDistributed += actualTransferred;
                remainingAmount -= actualTransferred;
            }
        }

        if (totalDistributed > 0) {
            updateTotalEnergy();
        }
        return totalDistributed;
    }

    /**
     * 네트워크에서 에너지를 요청합니다
     * @param requester 에너지를 요청하는 테서랙트
     * @param amount 요청할 에너지 양
     * @return 실제로 받은 에너지 양
     */
    public long requestEnergy(TesseractBlockEntity requester, long amount) {
        if (amount <= 0 || requester == null) return 0;

        List<TesseractBlockEntity> suppliers = tesseracts.stream()
                .filter(Objects::nonNull)
                .filter(t -> !t.isRemoved())
                .filter(t -> t != requester)
                .filter(t -> canSendEnergy(t))
                .filter(t -> t.getStoredEnergy() > 0)
                .sorted(Comparator.comparingDouble(t -> -(t.getStoredEnergy() / (double)t.getMaxEnergy())))
                .toList();

        if (suppliers.isEmpty()) return 0;

        long totalReceived = 0;
        long remainingAmount = amount;

        // 에너지가 많은 테서랙트부터 우선적으로 추출
        for (TesseractBlockEntity supplier : suppliers) {
            if (remainingAmount <= 0) break;

            long canSupply = Math.min(remainingAmount, supplier.getStoredEnergy());

            if (canSupply > 0) {
                long actualExtracted = supplier.extractEnergy(canSupply, false);
                totalReceived += actualExtracted;
                remainingAmount -= actualExtracted;
            }
        }

        if (totalReceived > 0) {
            updateTotalEnergy();
        }
        return totalReceived;
    }

    /**
     * 네트워크에 아이템을 분배합니다
     * @param sender 아이템을 보내는 테서랙트
     * @param stack 보낼 아이템 스택
     * @return 분배되지 않은 아이템 스택
     */
    public ItemStack distributeItem(TesseractBlockEntity sender, ItemStack stack) {
        if (stack.isEmpty() || sender == null) return stack;

        List<TesseractBlockEntity> receivers = tesseracts.stream()
                .filter(Objects::nonNull)
                .filter(t -> !t.isRemoved())
                .filter(t -> t != sender)
                .filter(t -> canReceiveItems(t))
                .filter(t -> hasSpaceForItem(t, stack))
                .toList();

        if (receivers.isEmpty()) return stack;

        ItemStack remaining = stack.copy();

        // 거리나 우선순위 기준으로 정렬할 수 있지만, 여기서는 단순히 셔플
        List<TesseractBlockEntity> shuffledReceivers = new ArrayList<>(receivers);
        Collections.shuffle(shuffledReceivers);

        for (TesseractBlockEntity receiver : shuffledReceivers) {
            if (remaining.isEmpty()) break;

            remaining = insertItemIntoTesseract(receiver, remaining);
        }

        return remaining;
    }

    private boolean canSendEnergy(TesseractBlockEntity tesseract) {
        TesseractBlockEntity.TesseractMode mode = tesseract.getMode();
        return mode == TesseractBlockEntity.TesseractMode.SEND_ONLY ||
                mode == TesseractBlockEntity.TesseractMode.SEND_RECEIVE;
    }

    private boolean canReceiveEnergy(TesseractBlockEntity tesseract) {
        TesseractBlockEntity.TesseractMode mode = tesseract.getMode();
        return mode == TesseractBlockEntity.TesseractMode.RECEIVE_ONLY ||
                mode == TesseractBlockEntity.TesseractMode.SEND_RECEIVE;
    }

    private boolean canReceiveItems(TesseractBlockEntity tesseract) {
        TesseractBlockEntity.TesseractMode mode = tesseract.getMode();
        return mode == TesseractBlockEntity.TesseractMode.RECEIVE_ONLY ||
                mode == TesseractBlockEntity.TesseractMode.SEND_RECEIVE;
    }

    private boolean hasSpaceForItem(TesseractBlockEntity tesseract, ItemStack stack) {
        try {
            for (int i = 0; i < tesseract.size(); i++) {
                ItemStack slotStack = tesseract.getStack(i);
                if (slotStack.isEmpty()) return true;
                if (ItemStack.canCombine(slotStack, stack) &&
                        slotStack.getCount() + stack.getCount() <= slotStack.getMaxCount()) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 예외 발생 시 안전하게 false 반환
            return false;
        }
        return false;
    }

    private ItemStack insertItemIntoTesseract(TesseractBlockEntity tesseract, ItemStack stack) {
        if (stack.isEmpty() || tesseract == null) return stack;

        ItemStack remaining = stack.copy();

        try {
            // 기존 스택과 합칠 수 있는 슬롯 우선 검사
            for (int i = 0; i < tesseract.size() && !remaining.isEmpty(); i++) {
                ItemStack slotStack = tesseract.getStack(i);
                if (!slotStack.isEmpty() && ItemStack.canCombine(slotStack, remaining)) {
                    int canInsert = slotStack.getMaxCount() - slotStack.getCount();
                    int toInsert = Math.min(canInsert, remaining.getCount());

                    if (toInsert > 0) {
                        slotStack.increment(toInsert);
                        remaining.decrement(toInsert);
                        tesseract.markDirty();
                    }
                }
            }

            // 빈 슬롯에 삽입
            for (int i = 0; i < tesseract.size() && !remaining.isEmpty(); i++) {
                ItemStack slotStack = tesseract.getStack(i);
                if (slotStack.isEmpty()) {
                    tesseract.setStack(i, remaining.copy());
                    remaining = ItemStack.EMPTY;
                    tesseract.markDirty();
                    break;
                }
            }
        } catch (Exception e) {
            // 예외 발생 시 원본 스택 반환
            return stack;
        }

        return remaining;
    }

    // 정리 메서드 - 제거된 테서랙트들을 네트워크에서 제거
    public void cleanup() {
        tesseracts.removeIf(t -> t == null || t.isRemoved());
        updateTotalEnergy();
    }

    // 네트워크 정보 조회 메서드들
    public UUID getNetworkId() {
        return networkId;
    }

    public String getNetworkName() {
        return networkName;
    }

    public Set<TesseractBlockEntity> getTesseracts() {
        cleanup(); // 조회 시 정리 수행
        return new HashSet<>(tesseracts);
    }

    public int getTesseractCount() {
        cleanup();
        return tesseracts.size();
    }

    public long getTotalStoredEnergy() {
        updateTotalEnergy();
        return totalStoredEnergy;
    }

    public long getTotalMaxEnergy() {
        return tesseracts.stream()
                .filter(Objects::nonNull)
                .filter(t -> !t.isRemoved())
                .mapToLong(TesseractBlockEntity::getMaxEnergy)
                .sum();
    }

    public List<TesseractInfo> getTesseractInfoList() {
        return tesseracts.stream()
                .filter(Objects::nonNull)
                .filter(t -> !t.isRemoved())
                .map(t -> new TesseractInfo(
                        t.getTesseractName().isEmpty() ? "Unnamed" : t.getTesseractName(),
                        t.getPos(),
                        t.getStoredEnergy(),
                        t.getMaxEnergy(),
                        t.getMode(),
                        !t.isEmpty()
                ))
                .sorted(Comparator.comparing(TesseractInfo::name))
                .toList();
    }

    public record TesseractInfo(
            String name,
            BlockPos pos,
            long storedEnergy,
            long maxEnergy,
            TesseractBlockEntity.TesseractMode mode,
            boolean hasItems
    ) {
        public double getEnergyPercentage() {
            return maxEnergy > 0 ? (double) storedEnergy / maxEnergy * 100 : 0;
        }
    }

    public boolean isEmpty() {
        cleanup();
        return tesseracts.isEmpty();
    }
}