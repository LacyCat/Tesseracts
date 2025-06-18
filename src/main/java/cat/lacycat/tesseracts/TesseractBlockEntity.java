package cat.lacycat.tesseracts;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TesseractBlockEntity extends BlockEntity {
    private float rotationTime = 0.0f;

    public TesseractBlockEntity(BlockPos pos, BlockState state) {
        super(TesseractMod.TESSERACT_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, TesseractBlockEntity blockEntity) {
        if (world.isClient) {
            blockEntity.rotationTime += 0.02f;
            if (blockEntity.rotationTime > Math.PI * 2) {
                blockEntity.rotationTime = 0.0f;
            }
        }
    }

    public float getRotationTime() {
        return rotationTime;
    }
}