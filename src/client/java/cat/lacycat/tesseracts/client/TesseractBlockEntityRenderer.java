package cat.lacycat.tesseracts.client;

import cat.lacycat.tesseracts.TesseractBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderLayer;

public class TesseractBlockEntityRenderer implements BlockEntityRenderer<TesseractBlockEntity> {

    // 4D 테서렉트의 정점들 (4차원 좌표) - static final로 메모리 효율성 향상
    private static final Vector4f[] TESSERACT_VERTICES = {
            // 첫 번째 큐브 (w = -0.5)
            new Vector4f(-0.5f, -0.5f, -0.5f, -0.5f), // 0
            new Vector4f( 0.5f, -0.5f, -0.5f, -0.5f), // 1
            new Vector4f( 0.5f,  0.5f, -0.5f, -0.5f), // 2
            new Vector4f(-0.5f,  0.5f, -0.5f, -0.5f), // 3
            new Vector4f(-0.5f, -0.5f,  0.5f, -0.5f), // 4
            new Vector4f( 0.5f, -0.5f,  0.5f, -0.5f), // 5
            new Vector4f( 0.5f,  0.5f,  0.5f, -0.5f), // 6
            new Vector4f(-0.5f,  0.5f,  0.5f, -0.5f), // 7

            // 두 번째 큐브 (w = 0.5)
            new Vector4f(-0.5f, -0.5f, -0.5f,  0.5f), // 8
            new Vector4f( 0.5f, -0.5f, -0.5f,  0.5f), // 9
            new Vector4f( 0.5f,  0.5f, -0.5f,  0.5f), // 10
            new Vector4f(-0.5f,  0.5f, -0.5f,  0.5f), // 11
            new Vector4f(-0.5f, -0.5f,  0.5f,  0.5f), // 12
            new Vector4f( 0.5f, -0.5f,  0.5f,  0.5f), // 13
            new Vector4f( 0.5f,  0.5f,  0.5f,  0.5f), // 14
            new Vector4f(-0.5f,  0.5f,  0.5f,  0.5f)  // 15
    };

    // 테서렉트의 모서리들
    private static final int[][] TESSERACT_EDGES = {
            // 첫 번째 큐브의 모서리들
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, // 아래 면
            {4, 5}, {5, 6}, {6, 7}, {7, 4}, // 위 면
            {0, 4}, {1, 5}, {2, 6}, {3, 7}, // 수직 모서리들

            // 두 번째 큐브의 모서리들
            {8, 9}, {9, 10}, {10, 11}, {11, 8}, // 아래 면
            {12, 13}, {13, 14}, {14, 15}, {15, 12}, // 위 면
            {8, 12}, {9, 13}, {10, 14}, {11, 15}, // 수직 모서리들

            // 두 큐브를 연결하는 모서리들
            {0, 8}, {1, 9}, {2, 10}, {3, 11},
            {4, 12}, {5, 13}, {6, 14}, {7, 15}
    };

    // 재사용 가능한 객체들로 GC 압박 줄이기
    private final Vector4f tempVector4f = new Vector4f();
    private final Matrix4f tempMatrix = new Matrix4f();
    private final Vector3f[] projectedVertices = new Vector3f[TESSERACT_VERTICES.length];

    // 캐시된 회전 매트릭스들
    private final Matrix4f[] rotationMatrices = new Matrix4f[6];

    // 원기둥 렌더링을 위한 상수들
    private static final int CYLINDER_SEGMENTS = 8; // 원기둥의 둘레 분할 수
    private static final float CYLINDER_RADIUS = 0.02f; // 원기둥 반지름

    public TesseractBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        // 투영된 정점 배열 초기화
        for (int i = 0; i < projectedVertices.length; i++) {
            projectedVertices[i] = new Vector3f();
        }

        // 회전 매트릭스 배열 초기화
        for (int i = 0; i < rotationMatrices.length; i++) {
            rotationMatrices[i] = new Matrix4f();
        }
    }

    @Override
    public void render(TesseractBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        int lightValue = 0xF000F0;
        // null 체크 추가
        if (entity == null || entity.getWorld() == null) {
            return;
        }

        // 클라이언트 측에서만 렌더링 (서버 크래시 방지)
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        // try-catch로 렌더링 오류 방지
        try {
            matrices.push();
            matrices.translate(0.5f, 0.5f, 0.5f);

            // 시간 계산 최적화 및 안전장치
            long worldTime = entity.getWorld().getTime();
            float time = (worldTime + tickDelta) * 0.01f; // 회전 속도를 더 느리게

            // 플레이어 위치 기반 회전 (null 체크 추가)
            if (client.player != null) {
                double dx = client.player.getX() - (entity.getPos().getX() + 0.5);
                double dz = client.player.getZ() - (entity.getPos().getZ() + 0.5);

                // 거리가 너무 가까우면 회전하지 않음 (NaN 방지)
                if (dx * dx + dz * dz > 0.01) {
                    float angle = (float) Math.atan2(dz, dx);
                    if (Float.isFinite(angle)) {
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(angle));
                    }
                }
            }

            // 4D 회전 매트릭스들을 미리 계산하고 재사용
            createSafeRotationMatrices(time);

            // 모든 4D 회전을 결합 (안전하게)
            Matrix4f totalRotation = tempMatrix.identity();
            for (Matrix4f rotMatrix : rotationMatrices) {
                if (rotMatrix != null && isMatrixValid(rotMatrix)) {
                    totalRotation.mul(rotMatrix);
                }
            }

            // 회전된 정점들을 3D로 투영 (기존 배열 재사용)
            projectVerticesSafely(totalRotation);

            // 원기둥 모서리들을 렌더링 (투명도 적용)
            renderCylindricalEdges(matrices, vertexConsumers, time, lightValue);

        } catch (Exception e) {
            // 렌더링 오류 발생 시 로그만 남기고 크래시 방지
            // System.err.println("Tesseract rendering error: " + e.getMessage());
        } finally {
            matrices.pop();
        }
    }

    private void createSafeRotationMatrices(float time) {
        // 회전 속도를 더욱 줄여서 안정성 향상
        float[] angles = {
                time * 0.1f, // XY
                time * 0.08f, // XZ
                time * 0.12f, // XW
                time * 0.09f, // YZ
                time * 0.11f, // YW
                time * 0.07f  // ZW
        };

        int[][] axisPairs = {{0,1}, {0,2}, {0,3}, {1,2}, {1,3}, {2,3}};

        for (int i = 0; i < rotationMatrices.length; i++) {
            try {
                createSafeRotationMatrix4D(rotationMatrices[i], axisPairs[i][0], axisPairs[i][1], angles[i]);
            } catch (Exception e) {
                // 오류 발생 시 단위 행렬로 설정
                rotationMatrices[i].identity();
            }
        }
    }

    private void projectVerticesSafely(Matrix4f totalRotation) {
        for (int i = 0; i < TESSERACT_VERTICES.length; i++) {
            try {
                tempVector4f.set(TESSERACT_VERTICES[i]);
                totalRotation.transform(tempVector4f);

                // 4D에서 3D로 투영 (원근 투영) - 안전장치 강화
                float w = tempVector4f.w + 2.5f; // w 좌표를 더 크게 조정
                float scale = 1.0f / Math.max(Math.abs(w), 0.5f); // 최소값을 더 크게 설정

                // NaN 및 무한대 체크
                if (!Float.isFinite(scale)) {
                    scale = 1.0f;
                }

                float x = tempVector4f.x * scale * 0.4f; // 크기를 더 작게
                float y = tempVector4f.y * scale * 0.4f;
                float z = tempVector4f.z * scale * 0.4f;

                // 최종 좌표 범위 제한
                x = Math.max(-2.0f, Math.min(2.0f, x));
                y = Math.max(-2.0f, Math.min(2.0f, y));
                z = Math.max(-2.0f, Math.min(2.0f, z));

                projectedVertices[i].set(x, y, z);

            } catch (Exception e) {
                // 오류 발생 시 원점으로 설정
                projectedVertices[i].set(0, 0, 0);
            }
        }
    }

    private void renderCylindricalEdges(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float time, int light) {
        try {
            // 투명도를 위해 TranslucentLayers 사용
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getSolid());
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

            // 색상 계산 안전장치
            float baseR = 0.6f + 0.3f * (float) Math.sin(time);
            float baseG = 0.6f + 0.3f * (float) Math.cos(time * 1.1f);
            float baseB = 0.6f + 0.3f * (float) Math.sin(time * 0.9f);

            for (int edgeIndex = 0; edgeIndex < TESSERACT_EDGES.length; edgeIndex++) {
                int[] edge = TESSERACT_EDGES[edgeIndex];

                if (edge.length >= 2 && edge[0] >= 0 && edge[0] < projectedVertices.length
                        && edge[1] >= 0 && edge[1] < projectedVertices.length) {

                    Vector3f start = projectedVertices[edge[0]];
                    Vector3f end = projectedVertices[edge[1]];

                    // null 체크
                    if (start == null || end == null) continue;

                    // 좌표가 유효한지 확인
                    if (!isVectorValid(start) || !isVectorValid(end)) continue;

                    // 색상 변화를 더 안전하게 - 각 모서리마다 다른 색상
                    float colorVariation = edgeIndex * 0.1f;
                    float r = Math.max(0.4f, Math.min(1.0f, baseR + colorVariation));
                    float g = Math.max(0.4f, Math.min(1.0f, baseG + colorVariation));
                    float b = Math.max(0.4f, Math.min(1.0f, baseB + colorVariation));

                    // 투명도 적용 - 뒤쪽 선도 보이게
                    float alpha = 1f;

                    // 원기둥 모서리 렌더링
                    renderCylindricalEdge(vertexConsumer, positionMatrix, normalMatrix,
                            start, end, r, g, b, alpha, light);
                }
            }
        } catch (Exception e) {
            // 렌더링 오류 무시
        }
    }

    private void renderCylindricalEdge(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix,
                                       Vector3f start, Vector3f end, float r, float g, float b, float alpha, int light) {

        // 모서리 방향 벡터 계산
        Vector3f direction = new Vector3f(end).sub(start);
        float length = direction.length();

        if (length < 0.001f) return; // 너무 짧은 모서리는 무시

        direction.normalize();

        // 원기둥의 축에 수직인 두 벡터 생성
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(direction).cross(up);

        // 만약 direction이 up과 평행하면 다른 벡터 사용
        if (right.lengthSquared() < 0.001f) {
            up.set(1, 0, 0);
            right = new Vector3f(direction).cross(up);
        }

        right.normalize();
        up = new Vector3f(right).cross(direction);

        // 원기둥의 둘레 점들 생성 및 렌더링
        Vector3f[] startCircle = new Vector3f[CYLINDER_SEGMENTS];
        Vector3f[] endCircle = new Vector3f[CYLINDER_SEGMENTS];

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            float angle = (float) (2 * Math.PI * i / CYLINDER_SEGMENTS);
            float cos = (float) Math.cos(angle) * CYLINDER_RADIUS;
            float sin = (float) Math.sin(angle) * CYLINDER_RADIUS;

            // 시작점의 원 위의 점
            startCircle[i] = new Vector3f(start)
                    .add(new Vector3f(right).mul(cos))
                    .add(new Vector3f(up).mul(sin));

            // 끝점의 원 위의 점
            endCircle[i] = new Vector3f(end)
                    .add(new Vector3f(right).mul(cos))
                    .add(new Vector3f(up).mul(sin));
        }

        // 원기둥 표면 렌더링 (사각형들로 구성)
        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            int next = (i + 1) % CYLINDER_SEGMENTS;

            // 법선 벡터 계산 (바깥쪽 방향)
            Vector3f normal = new Vector3f(right).mul((float) Math.cos(2 * Math.PI * i / CYLINDER_SEGMENTS))
                    .add(new Vector3f(up).mul((float) Math.sin(2 * Math.PI * i / CYLINDER_SEGMENTS)));

            // 사각형 렌더링 (2개의 삼각형)
            // 첫 번째 삼각형
            addVertex(vertexConsumer, positionMatrix, normalMatrix, startCircle[i], normal, r, g, b, alpha, light);
            addVertex(vertexConsumer, positionMatrix, normalMatrix, endCircle[i], normal, r, g, b, alpha, light);
            addVertex(vertexConsumer, positionMatrix, normalMatrix, startCircle[next], normal, r, g, b, alpha, light);

            // 두 번째 삼각형
            addVertex(vertexConsumer, positionMatrix, normalMatrix, startCircle[next], normal, r, g, b, alpha, light);
            addVertex(vertexConsumer, positionMatrix, normalMatrix, endCircle[i], normal, r, g, b, alpha, light);
            addVertex(vertexConsumer, positionMatrix, normalMatrix, endCircle[next], normal, r, g, b, alpha, light);
        }
    }

    private void addVertex(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix,
                           Vector3f pos, Vector3f normal, float r, float g, float b, float alpha, int light) {
        vertexConsumer.vertex(positionMatrix, pos.x, pos.y, pos.z)
                .color(r, g, b, alpha)
                .texture(0, 0) // UV 좌표
                .overlay(0)
                .light(light)
                .normal(normalMatrix, normal.x, normal.y, normal.z)
                .next();
    }

    // 4D 회전 매트릭스 생성 (안전장치 추가)
    private void createSafeRotationMatrix4D(Matrix4f matrix, int axis1, int axis2, float angle) {
        matrix.identity();

        // 각도 범위 제한
        angle = angle % (float)(2 * Math.PI);

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        // NaN 체크
        if (!Float.isFinite(cos) || !Float.isFinite(sin)) {
            return; // 단위 행렬 유지
        }

        // 4x4 매트릭스에서 해당 축들에 대한 회전 설정
        switch (axis1 * 10 + axis2) {
            case 1: // XY 회전 (0,1)
                matrix.m00(cos); matrix.m01(-sin);
                matrix.m10(sin); matrix.m11(cos);
                break;
            case 2: // XZ 회전 (0,2)
                matrix.m00(cos); matrix.m02(-sin);
                matrix.m20(sin); matrix.m22(cos);
                break;
            case 3: // XW 회전 (0,3)
                matrix.m00(cos); matrix.m03(-sin);
                matrix.m30(sin); matrix.m33(cos);
                break;
            case 12: // YZ 회전 (1,2)
                matrix.m11(cos); matrix.m12(-sin);
                matrix.m21(sin); matrix.m22(cos);
                break;
            case 13: // YW 회전 (1,3)
                matrix.m11(cos); matrix.m13(-sin);
                matrix.m31(sin); matrix.m33(cos);
                break;
            case 23: // ZW 회전 (2,3)
                matrix.m22(cos); matrix.m23(-sin);
                matrix.m32(sin); matrix.m33(cos);
                break;
        }
    }

    // 매트릭스 유효성 검사
    private boolean isMatrixValid(Matrix4f matrix) {
        // 주요 요소들이 finite한지 확인
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m11()) &&
                Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m33());
    }

    // 벡터 유효성 검사
    private boolean isVectorValid(Vector3f vector) {
        return Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z) &&
                Math.abs(vector.x) < 100 && Math.abs(vector.y) < 100 && Math.abs(vector.z) < 100;
    }

    @Override
    public boolean rendersOutsideBoundingBox(TesseractBlockEntity blockEntity) {
        return true; // 4D 투영으로 인해 경계 박스를 벗어날 수 있음
    }
}