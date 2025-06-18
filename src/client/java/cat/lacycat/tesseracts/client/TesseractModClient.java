package cat.lacycat.tesseracts.client;

import cat.lacycat.tesseracts.TesseractMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;

public class TesseractModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(TesseractMod.TESSERACT_BLOCK, RenderLayer.getTranslucent());
        BlockEntityRendererRegistry.register(TesseractMod.TESSERACT_BLOCK_ENTITY, TesseractBlockEntityRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(TesseractMod.TESSERACT_ITEM, new TesseractItemRenderer());
    }
}