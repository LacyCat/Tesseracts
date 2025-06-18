package cat.lacycat.tesseracts.client;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderLayer;

public class TesseractItemRenderer implements BuiltinItemRenderer {

    // 4D 테서렉트의 정점들 (4차원 좌표) - 아이템용으로 크기 조정
    private static final Vector4f[] TESSERACT_VERTICES = {
            // 첫 번째 큐브 (w = -0.3)
            new Vector4f(-0.3f, -0.3f, -0.3f, -0.3f), // 0
            new Vector4f( 0.3f, -0.3f, -0.3f, -0.3f), // 1
            new Vector4f( 0.3f,  0.3f, -0.3f, -0.3f), // 2
            new Vector4f(-0.3f,  0.3f, -0.3f, -0.3f), // 3
            new Vector4f(-0.3f, -0.3f,  0.3f, -0.3f), // 4
            new Vector4f( 0.3f, -0.3f,  0.3f, -0.3f), // 5
            new Vector4f( 0.3f,  0.3f,  0.3f, -0.3f), // 6
            new Vector4f(-0.3f,  0.3f,  0.3f, -0.3f), // 7

            // 두 번째 큐브 (w = 0.3)
            new Vector4f(-0.3f, -0.3f, -0.3f,  0.3f), // 8
            new Vector4f( 0.3f, -0.3f, -0.3f,  0.3f), // 9
            new Vector4f( 0.3f,  0.3f, -0.3f,  0.3f), // 10
            new Vector4f(-0.3f,  0.3f, -0.3f,  0.3f), // 11
            new Vector4f(-0.3f, -0.3f,  0.3f,  0.3f), // 12
            new Vector4f( 0.3f, -0.3f,  0.3f,  0.3f), // 13
            new Vector4f( 0.3f,  0.3f,  0.3f,  0.3f), // 14
            new Vector4f(-0.3f,  0.3f,  0.3f,  0.3f)  // 15
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

    // 원기둥 렌더링을 위한 상수들 - 아이템용으로 더 세밀하게
    private static final int CYLINDER_SEGMENTS = 6; // 성능을 위해 줄임
    private static final float CYLINDER_RADIUS = 0.015f; // 더 얇게

    public TesseractItemRenderer() {
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
    public void render(ItemStack stack, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {

        try {
            matrices.push();

            // 아이템 렌더링 모드에 따른 변환 적용
            applyTransformations(matrices,ModelTransformationMode.GUI);

            // 시간 계산 - 아이템은 더 천천히 회전
            long time = System.currentTimeMillis();
            float animTime = time * 0.002f; // 더 느린 회전

            // 4D 회전 매트릭스들을 미리 계산하고 재사용
            createSafeRotationMatrices(animTime);

            // 모든 4D 회전을 결합
            Matrix4f totalRotation = tempMatrix.identity();
            for (Matrix4f rotMatrix : rotationMatrices) {
                if (rotMatrix != null && isMatrixValid(rotMatrix)) {
                    totalRotation.mul(rotMatrix);
                }
            }

            // 회전된 정점들을 3D로 투영
            projectVerticesSafely(totalRotation);

            // 원기둥 모서리들을 렌더링
            renderCylindricalEdges(matrices, vertexConsumers, animTime, light);

        } catch (Exception e) {
            // 렌더링 오류 발생 시 무시
        } finally {
            matrices.pop();
        }
    }

    private void applyTransformations(MatrixStack matrices, ModelTransformationMode mode) {
        switch (mode) {
            case GUI:
                // 인벤토리에서의 변환
                matrices.translate(0.5f, 0.5f, 0.0f);
                matrices.scale(0.8f, 0.8f, 0.8f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45));
                break;
            case GROUND:
                // 땅에 떨어진 아이템
                matrices.translate(0.5f, 0.2f, 0.5f);
                matrices.scale(0.6f, 0.6f, 0.6f);
                break;
            case FIXED:
                // 아이템 프레임 등
                matrices.translate(0.5f, 0.5f, 0.5f);
                matrices.scale(0.8f, 0.8f, 0.8f);
                break;
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                // 1인칭 손에 든 상태
                matrices.translate(0.4f, 0.4f, 0.4f);
                matrices.scale(0.5f, 0.5f, 0.5f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45));
                break;
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                // 3인칭 손에 든 상태
                matrices.translate(0.0f, 0.1f, 0.1f);
                matrices.scale(0.4f, 0.4f, 0.4f);
                break;
            case HEAD:
                // 머리에 착용
                matrices.translate(0.0f, -0.25f, 0.0f);
                matrices.scale(0.6f, 0.6f, 0.6f);
                break;
            default:
                matrices.translate(0.5f, 0.5f, 0.5f);
                matrices.scale(0.8f, 0.8f, 0.8f);
                break;
        }
    }

    private void createSafeRotationMatrices(float time) {
        // 아이템용 회전 속도 - 더 느리게
        float[] angles = {
                time * 0.05f, // XY
                time * 0.04f, // XZ
                time * 0.06f, // XW
                time * 0.045f, // YZ
                time * 0.055f, // YW
                time * 0.035f  // ZW
        };

        int[][] axisPairs = {{0,1}, {0,2}, {0,3}, {1,2}, {1,3}, {2,3}};

        for (int i = 0; i < rotationMatrices.length; i++) {
            try {
                createSafeRotationMatrix4D(rotationMatrices[i], axisPairs[i][0], axisPairs[i][1], angles[i]);
            } catch (Exception e) {
                rotationMatrices[i].identity();
            }
        }
    }

    private void projectVerticesSafely(Matrix4f totalRotation) {
        for (int i = 0; i < TESSERACT_VERTICES.length; i++) {
            try {
                tempVector4f.set(TESSERACT_VERTICES[i]);
                totalRotation.transform(tempVector4f);

                // 4D에서 3D로 투영 - 아이템용으로 조정
                float w = tempVector4f.w + 2.0f;
                float scale = 1.0f / Math.max(Math.abs(w), 0.5f);

                if (!Float.isFinite(scale)) {
                    scale = 1.0f;
                }

                float x = tempVector4f.x * scale * 0.6f; // 아이템 크기에 맞게 조정
                float y = tempVector4f.y * scale * 0.6f;
                float z = tempVector4f.z * scale * 0.6f;

                // 좌표 범위 제한
                x = Math.max(-1.5f, Math.min(1.5f, x));
                y = Math.max(-1.5f, Math.min(1.5f, y));
                z = Math.max(-1.5f, Math.min(1.5f, z));

                projectedVertices[i].set(x, y, z);

            } catch (Exception e) {
                projectedVertices[i].set(0, 0, 0);
            }
        }
    }

    private void renderCylindricalEdges(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float time, int light) {
        try {
            // 불투명 렌더링
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getSolid());
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

            // 아이템용 색상 - 더 밝고 선명하게
            float baseR = 0.7f + 0.3f * (float) Math.sin(time);
            float baseG = 0.7f + 0.3f * (float) Math.cos(time * 1.1f);
            float baseB = 0.7f + 0.3f * (float) Math.sin(time * 0.9f);

            // 고정된 밝은 조명값
            int lightValue = 0xF000F0;

            for (int edgeIndex = 0; edgeIndex < TESSERACT_EDGES.length; edgeIndex++) {
                int[] edge = TESSERACT_EDGES[edgeIndex];

                if (edge.length >= 2 && edge[0] >= 0 && edge[0] < projectedVertices.length
                        && edge[1] >= 0 && edge[1] < projectedVertices.length) {

                    Vector3f start = projectedVertices[edge[0]];
                    Vector3f end = projectedVertices[edge[1]];

                    if (start == null || end == null) continue;
                    if (!isVectorValid(start) || !isVectorValid(end)) continue;

                    // 각 모서리마다 다른 색상
                    float colorVariation = edgeIndex * 0.1f;
                    float r = Math.max(0.5f, Math.min(1.0f, baseR + colorVariation));
                    float g = Math.max(0.5f, Math.min(1.0f, baseG + colorVariation));
                    float b = Math.max(0.5f, Math.min(1.0f, baseB + colorVariation));

                    float alpha = 1.0f; // 불투명

                    // 원기둥 모서리 렌더링
                    renderCylindricalEdge(vertexConsumer, positionMatrix, normalMatrix,
                            start, end, r, g, b, alpha, lightValue);
                }
            }
        } catch (Exception e) {
            // 렌더링 오류 무시
        }
    }

    private void renderCylindricalEdge(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix,
                                       Vector3f start, Vector3f end, float r, float g, float b, float alpha, int light) {

        Vector3f direction = new Vector3f(end).sub(start);
        float length = direction.length();

        if (length < 0.001f) return;

        direction.normalize();

        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(direction).cross(up);

        if (right.lengthSquared() < 0.001f) {
            up.set(1, 0, 0);
            right = new Vector3f(direction).cross(up);
        }

        right.normalize();
        up = new Vector3f(right).cross(direction);

        Vector3f[] startCircle = new Vector3f[CYLINDER_SEGMENTS];
        Vector3f[] endCircle = new Vector3f[CYLINDER_SEGMENTS];

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            float angle = (float) (2 * Math.PI * i / CYLINDER_SEGMENTS);
            float cos = (float) Math.cos(angle) * CYLINDER_RADIUS;
            float sin = (float) Math.sin(angle) * CYLINDER_RADIUS;

            startCircle[i] = new Vector3f(start)
                    .add(new Vector3f(right).mul(cos))
                    .add(new Vector3f(up).mul(sin));

            endCircle[i] = new Vector3f(end)
                    .add(new Vector3f(right).mul(cos))
                    .add(new Vector3f(up).mul(sin));
        }

        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            int next = (i + 1) % CYLINDER_SEGMENTS;

            Vector3f normal = new Vector3f(right).mul((float) Math.cos(2 * Math.PI * i / CYLINDER_SEGMENTS))
                    .add(new Vector3f(up).mul((float) Math.sin(2 * Math.PI * i / CYLINDER_SEGMENTS)));

            // 삼각형 렌더링
            addVertex(vertexConsumer, positionMatrix, normalMatrix, startCircle[i], normal, r, g, b, alpha, light);
            addVertex(vertexConsumer, positionMatrix, normalMatrix, endCircle[i], normal, r, g, b, alpha, light);
            addVertex(vertexConsumer, positionMatrix, normalMatrix, startCircle[next], normal, r, g, b, alpha, light);

            addVertex(vertexConsumer, positionMatrix, normalMatrix, startCircle[next], normal, r, g, b, alpha, light);
            addVertex(vertexConsumer, positionMatrix, normalMatrix, endCircle[i], normal, r, g, b, alpha, light);
            addVertex(vertexConsumer, positionMatrix, normalMatrix, endCircle[next], normal, r, g, b, alpha, light);
        }
    }

    private void addVertex(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix,
                           Vector3f pos, Vector3f normal, float r, float g, float b, float alpha, int light) {
        vertexConsumer.vertex(positionMatrix, pos.x, pos.y, pos.z)
                .color(r, g, b, alpha)
                .texture(0.0f,0.0f)
                .overlay(0)
                .light(light)
                .normal(normalMatrix, normal.x, normal.y, normal.z)
                .next();
    }

    private void createSafeRotationMatrix4D(Matrix4f matrix, int axis1, int axis2, float angle) {
        matrix.identity();

        angle = angle % (float)(2 * Math.PI);

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        if (!Float.isFinite(cos) || !Float.isFinite(sin)) {
            return;
        }

        switch (axis1 * 10 + axis2) {
            case 1: // XY 회전
                matrix.m00(cos); matrix.m01(-sin);
                matrix.m10(sin); matrix.m11(cos);
                break;
            case 2: // XZ 회전
                matrix.m00(cos); matrix.m02(-sin);
                matrix.m20(sin); matrix.m22(cos);
                break;
            case 3: // XW 회전
                matrix.m00(cos); matrix.m03(-sin);
                matrix.m30(sin); matrix.m33(cos);
                break;
            case 12: // YZ 회전
                matrix.m11(cos); matrix.m12(-sin);
                matrix.m21(sin); matrix.m22(cos);
                break;
            case 13: // YW 회전
                matrix.m11(cos); matrix.m13(-sin);
                matrix.m31(sin); matrix.m33(cos);
                break;
            case 23: // ZW 회전
                matrix.m22(cos); matrix.m23(-sin);
                matrix.m32(sin); matrix.m33(cos);
                break;
        }
    }

    private boolean isMatrixValid(Matrix4f matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m11()) &&
                Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m33());
    }

    private boolean isVectorValid(Vector3f vector) {
        return Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z) &&
                Math.abs(vector.x) < 100 && Math.abs(vector.y) < 100 && Math.abs(vector.z) < 100;
    }
}