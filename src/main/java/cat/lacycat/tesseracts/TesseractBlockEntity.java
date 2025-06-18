package cat.lacycat.tesseracts;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Random;

public class TesseractBlockEntity extends BlockEntity {
    private float rotationTime = 0.0f;
    private final Random random = new Random();
    private int particleTimer = 0;

    public TesseractBlockEntity(BlockPos pos, BlockState state) {
        super(TesseractMod.TESSERACT_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, TesseractBlockEntity blockEntity) {
        if (world.isClient) {
            blockEntity.rotationTime += 0.02f;
            if (blockEntity.rotationTime > Math.PI * 2) {
                blockEntity.rotationTime = 0.0f;
            }

            blockEntity.particleTimer++;
            blockEntity.spawnParticleEffects(world, pos);
        }
    }

    private void spawnParticleEffects(World world, BlockPos pos) {
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.5;
        double centerZ = pos.getZ() + 0.5;

        // 1. 차원 균열 파티클 (항상 생성)
        if (particleTimer % 3 == 0) {
            spawnDimensionalCrackParticles(world, centerX, centerY, centerZ);
        }

        // 2. 회전에 따라 색상이 변하는 오로라 효과
        if (particleTimer % 2 == 0) {
            spawnAuroraParticles(world, centerX, centerY, centerZ);
        }

        // 3. 플레이어 근접 시 에너지 파장
        PlayerEntity nearestPlayer = world.getClosestPlayer(centerX, centerY, centerZ, 8.0, false);
        if (nearestPlayer != null) {
            double distance = nearestPlayer.squaredDistanceTo(centerX, centerY, centerZ);
            if (distance < 25.0) { // 5블록 이내
                spawnEnergyWaveParticles(world, centerX, centerY, centerZ, nearestPlayer, distance);
            }
        }
    }

    private void spawnDimensionalCrackParticles(World world, double centerX, double centerY, double centerZ) {
        for (int i = 0; i < 3; i++) {
            // 균열 모양의 선형 파티클
            double angle = random.nextDouble() * Math.PI * 2;
            double length = 0.5 + random.nextDouble() * 1.5;

            for (double t = 0; t < length; t += 0.1) {
                double x = centerX + Math.cos(angle) * t;
                double y = centerY + (random.nextDouble() - 0.5) * 2.0;
                double z = centerZ + Math.sin(angle) * t;

                // 보라색 균열 효과
                Vector3f color = new Vector3f(0.4f + random.nextFloat() * 0.3f, 0.1f, 0.8f + random.nextFloat() * 0.2f);
                world.addParticle(new DustParticleEffect(color, 0.8f),
                        x, y, z,
                        (random.nextDouble() - 0.5) * 0.02,
                        (random.nextDouble() - 0.5) * 0.02,
                        (random.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    private void spawnAuroraParticles(World world, double centerX, double centerY, double centerZ) {
        // 회전에 따른 색상 계산 (HSV to RGB)
        float hue = (rotationTime / (float)(Math.PI * 2)) * 360.0f; // 0-360도
        Vector3f color = hsvToRgb(hue, 0.8f, 1.0f);

        // 나선형 오로라 효과
        for (int i = 0; i < 8; i++) {
            double spiralAngle = rotationTime + (i * Math.PI / 4);
            double radius = 1.2 + Math.sin(rotationTime * 2 + i) * 0.3;
            double height = Math.sin(rotationTime * 1.5 + i) * 1.5;

            double x = centerX + Math.cos(spiralAngle) * radius;
            double y = centerY + height;
            double z = centerZ + Math.sin(spiralAngle) * radius;

            world.addParticle(new DustParticleEffect(color, 1.2f),
                    x, y, z,
                    Math.cos(spiralAngle + Math.PI/2) * 0.01,
                    0.02,
                    Math.sin(spiralAngle + Math.PI/2) * 0.01);
        }
    }

    private void spawnEnergyWaveParticles(World world, double centerX, double centerY, double centerZ,
                                          PlayerEntity player, double distance) {
        Vec3d playerPos = player.getPos();
        Vec3d direction = new Vec3d(
                playerPos.x - centerX,
                playerPos.y - centerY + 0.5,
                playerPos.z - centerZ
        ).normalize();

        // 강도는 거리에 반비례
        double intensity = Math.max(0.1, 1.0 - (distance / 25.0));
        int particleCount = (int)(intensity * 15);

        for (int i = 0; i < particleCount; i++) {
            // 플레이어 방향으로 에너지 파장 생성
            double waveDistance = random.nextDouble() * 2.0;
            double x = centerX + direction.x * waveDistance + (random.nextDouble() - 0.5) * 0.5;
            double y = centerY + direction.y * waveDistance + (random.nextDouble() - 0.5) * 0.5;
            double z = centerZ + direction.z * waveDistance + (random.nextDouble() - 0.5) * 0.5;

            // 청록색 에너지 파장
            Vector3f waveColor = new Vector3f(0.2f, 1.0f, 0.8f + random.nextFloat() * 0.2f);
            world.addParticle(new DustParticleEffect(waveColor, 1.0f),
                    x, y, z,
                    direction.x * 0.05 + (random.nextDouble() - 0.5) * 0.02,
                    direction.y * 0.05 + (random.nextDouble() - 0.5) * 0.02,
                    direction.z * 0.05 + (random.nextDouble() - 0.5) * 0.02);
        }

        // 추가로 링 형태의 에너지 파동
        if (particleTimer % 10 == 0) {
            double ringRadius = 0.8 + Math.sin(rotationTime * 3) * 0.2;
            for (int i = 0; i < 12; i++) {
                double ringAngle = (i / 12.0) * Math.PI * 2;
                double x = centerX + Math.cos(ringAngle) * ringRadius;
                double y = centerY + Math.sin(rotationTime * 2) * 0.3;
                double z = centerZ + Math.sin(ringAngle) * ringRadius;

                Vector3f ringColor = new Vector3f(0.3f + (float)intensity * 0.7f, 0.8f, 1.0f);
                world.addParticle(new DustParticleEffect(ringColor, 0.8f),
                        x, y, z, 0, 0.01, 0);
            }
        }
    }

    // HSV to RGB 변환 함수
    private Vector3f hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs(((h / 60) % 2) - 1));
        float m = v - c;

        float r, g, b;

        if (h >= 0 && h < 60) {
            r = c; g = x; b = 0;
        } else if (h >= 60 && h < 120) {
            r = x; g = c; b = 0;
        } else if (h >= 120 && h < 180) {
            r = 0; g = c; b = x;
        } else if (h >= 180 && h < 240) {
            r = 0; g = x; b = c;
        } else if (h >= 240 && h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        return new Vector3f(r + m, g + m, b + m);
    }

    public float getRotationTime() {
        return rotationTime;
    }
}