package cat.lacycat.tesseracts;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TesseractNetworkManager extends PersistentState {
    private static final String DATA_NAME = "tesseract_networks";

    // 정적 네트워크 저장소 (모든 차원에서 공유)
    private static final Map<UUID, TesseractNetwork> networks = new ConcurrentHashMap<>();
    private static final Map<String, UUID> networkNameToId = new ConcurrentHashMap<>();

    // 인스턴스 데이터 (세이브 파일용)
    private final Map<UUID, String> networkIdToName = new HashMap<>();

    public TesseractNetworkManager() {
        super();
    }

    public static TesseractNetworkManager getServerState(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                nbt -> new TesseractNetworkManager().newone(nbt), // NbtCompound -> TesseractNetworkManager
                TesseractNetworkManager::new,                     // 기본 생성자 Supplier
                DATA_NAME
        );
    }

    /**
     * 네트워크에 테서랙트를 추가합니다
     */
    public static void addTesseract(UUID networkId, TesseractBlockEntity tesseract) {
        TesseractNetwork network = networks.get(networkId);
        if (network != null) {
            network.addTesseract(tesseract);
        }
    }

    /**
     * 네트워크에서 테서랙트를 제거합니다
     */
    public static void removeTesseract(UUID networkId, TesseractBlockEntity tesseract) {
        TesseractNetwork network = networks.get(networkId);
        if (network != null) {
            network.removeTesseract(tesseract);

            // 네트워크가 비어있으면 제거
            if (network.isEmpty()) {
                networks.remove(networkId);
                networkNameToId.entrySet().removeIf(entry -> entry.getValue().equals(networkId));
            }
        }
    }

    /**
     * 네트워크 이름으로 네트워크 ID를 가져오거나 생성합니다
     */
    public static UUID getOrCreateNetworkId(String networkName) {
        return networkNameToId.computeIfAbsent(networkName, name -> {
            UUID newId = UUID.randomUUID();
            TesseractNetwork network = new TesseractNetwork(newId, name);
            networks.put(newId, network);
            return newId;
        });
    }

    /**
     * 네트워크를 가져옵니다
     */
    public static TesseractNetwork getNetwork(UUID networkId) {
        return networks.get(networkId);
    }

    /**
     * 네트워크 이름으로 네트워크를 가져옵니다
     */
    public static TesseractNetwork getNetworkByName(String networkName) {
        UUID networkId = networkNameToId.get(networkName);
        return networkId != null ? networks.get(networkId) : null;
    }

    /**
     * 모든 네트워크 목록을 가져옵니다
     */
    public static List<TesseractNetwork> getAllNetworks() {
        return new ArrayList<>(networks.values());
    }

    /**
     * 모든 네트워크 이름 목록을 가져옵니다
     */
    public static Set<String> getAllNetworkNames() {
        return new HashSet<>(networkNameToId.keySet());
    }

    /**
     * 네트워크 통계를 가져옵니다
     */
    public static NetworkStats getNetworkStats() {
        int totalNetworks = networks.size();
        int totalTesseracts = networks.values().stream()
                .mapToInt(TesseractNetwork::getTesseractCount)
                .sum();
        long totalEnergy = networks.values().stream()
                .mapToLong(TesseractNetwork::getTotalStoredEnergy)
                .sum();
        long totalMaxEnergy = networks.values().stream()
                .mapToLong(TesseractNetwork::getTotalMaxEnergy)
                .sum();

        return new NetworkStats(totalNetworks, totalTesseracts, totalEnergy, totalMaxEnergy);
    }

    /**
     * 특정 위치 근처의 테서랙트들을 찾습니다 (디버깅용)
     */
    public static List<TesseractBlockEntity> findNearbyTesseracts(World world, double x, double y, double z, double radius) {
        List<TesseractBlockEntity> nearby = new ArrayList<>();

        for (TesseractNetwork network : networks.values()) {
            for (TesseractBlockEntity tesseract : network.getTesseracts()) {
                if (tesseract.getWorld() == world) {
                    double distance = Math.sqrt(
                            Math.pow(tesseract.getPos().getX() - x, 2) +
                                    Math.pow(tesseract.getPos().getY() - y, 2) +
                                    Math.pow(tesseract.getPos().getZ() - z, 2)
                    );

                    if (distance <= radius) {
                        nearby.add(tesseract);
                    }
                }
            }
        }

        return nearby;
    }

    /**
     * 네트워크를 강제로 동기화합니다 (월드 로드 시 사용)
     */
    public static void syncNetworks(ServerWorld world) {
        TesseractNetworkManager manager = getServerState(world);

        // 저장된 네트워크 이름들을 복원
        for (Map.Entry<UUID, String> entry : manager.networkIdToName.entrySet()) {
            UUID networkId = entry.getKey();
            String networkName = entry.getValue();

            if (!networks.containsKey(networkId)) {
                TesseractNetwork network = new TesseractNetwork(networkId, networkName);
                networks.put(networkId, network);
                networkNameToId.put(networkName, networkId);
            }
        }

        manager.markDirty();
    }

    // NBT 저장/로드
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        // 현재 활성 네트워크들의 정보를 저장
        networkIdToName.clear();
        for (TesseractNetwork network : networks.values()) {
            networkIdToName.put(network.getNetworkId(), network.getNetworkName());
        }

        NbtList networksList = new NbtList();
        for (Map.Entry<UUID, String> entry : networkIdToName.entrySet()) {
            NbtCompound networkNbt = new NbtCompound();
            networkNbt.putUuid("Id", entry.getKey());
            networkNbt.putString("Name", entry.getValue());
            networksList.add(networkNbt);
        }

        nbt.put("Networks", networksList);
        return nbt;
    }

    public TesseractNetworkManager newone(NbtCompound nbt) {
        TesseractNetworkManager manager = new TesseractNetworkManager();

        if (nbt.contains("Networks", NbtElement.LIST_TYPE)) {
            NbtList networksList = nbt.getList("Networks", NbtElement.COMPOUND_TYPE);

            for (int i = 0; i < networksList.size(); i++) {
                NbtCompound networkNbt = networksList.getCompound(i);
                UUID networkId = networkNbt.getUuid("Id");
                String networkName = networkNbt.getString("Name");

                manager.networkIdToName.put(networkId, networkName);

                // 메모리에 네트워크 생성 (테서랙트들은 나중에 로드될 때 자동으로 추가됨)
                if (!networks.containsKey(networkId)) {
                    TesseractNetwork network = new TesseractNetwork(networkId, networkName);
                    networks.put(networkId, network);
                    networkNameToId.put(networkName, networkId);
                }
            }
        }

        return manager;
    }

    /**
     * 네트워크 통계 정보를 담는 레코드
     */
    public record NetworkStats(
            int totalNetworks,
            int totalTesseracts,
            long totalStoredEnergy,
            long totalMaxEnergy
    ) {
        public double getGlobalEnergyPercentage() {
            return totalMaxEnergy > 0 ? (double) totalStoredEnergy / totalMaxEnergy * 100 : 0;
        }
    }

    /**
     * 디버그용 - 모든 네트워크 정보를 문자열로 반환
     */
    public static String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Tesseract Networks Debug Info ===\n");

        NetworkStats stats = getNetworkStats();
        sb.append(String.format("Total Networks: %d\n", stats.totalNetworks()));
        sb.append(String.format("Total Tesseracts: %d\n", stats.totalTesseracts()));
        sb.append(String.format("Total Energy: %d / %d (%.1f%%)\n",
                stats.totalStoredEnergy(), stats.totalMaxEnergy(), stats.getGlobalEnergyPercentage()));
        sb.append("\n");

        for (TesseractNetwork network : networks.values()) {
            sb.append(String.format("Network: %s (ID: %s)\n", network.getNetworkName(),
                    network.getNetworkId().toString().substring(0, 8)));
            sb.append(String.format("  Tesseracts: %d\n", network.getTesseractCount()));
            sb.append(String.format("  Energy: %d / %d (%.1f%%)\n",
                    network.getTotalStoredEnergy(), network.getTotalMaxEnergy(),
                    network.getTotalMaxEnergy() > 0 ?
                            (double) network.getTotalStoredEnergy() / network.getTotalMaxEnergy() * 100 : 0));

            for (TesseractNetwork.TesseractInfo info : network.getTesseractInfoList()) {
                sb.append(String.format("    - %s at %s [%s] (%.1f%% energy)%s\n",
                        info.name(), info.pos(), info.mode(),
                        info.getEnergyPercentage(),
                        info.hasItems() ? " *items*" : ""));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 네트워크 정리 - 빈 네트워크들을 제거합니다
     */
    public static void cleanup() {
        Iterator<Map.Entry<UUID, TesseractNetwork>> iterator = networks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TesseractNetwork> entry = iterator.next();
            TesseractNetwork network = entry.getValue();

            if (network.isEmpty()) {
                iterator.remove();
                networkNameToId.entrySet().removeIf(nameEntry ->
                        nameEntry.getValue().equals(entry.getKey()));
            }
        }
    }

    /**
     * 서버 종료 시 데이터 저장
     */
    public static void saveAll(ServerWorld world) {
        TesseractNetworkManager manager = getServerState(world);
        manager.markDirty();
    }
}