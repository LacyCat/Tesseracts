package cat.lacycat.tesseracts;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;

public class TesseractMod implements ModInitializer {
    public static final String MOD_ID = "tesseract_mod";

    public static final Block TESSERACT_BLOCK = Registry.register(
            Registries.BLOCK,
            new Identifier(MOD_ID, "tesseract_block"),
            new TesseractBlock(FabricBlockSettings.copyOf(Blocks.GLASS)
                    .strength(2.0f, 6.0f)
                    .sounds(BlockSoundGroup.GLASS)
                    .luminance(15)
                    .nonOpaque())
    );

    public static final BlockEntityType<TesseractBlockEntity> TESSERACT_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(MOD_ID, "tesseract_block_entity"),
            FabricBlockEntityTypeBuilder.create(TesseractBlockEntity::new, TESSERACT_BLOCK).build()
    );

    public static final Item TESSERACT_ITEM = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "tesseract_block"),
            new BlockItem(TESSERACT_BLOCK, new FabricItemSettings())
    );

    @Override
    public void onInitialize() {
        System.out.println("4D Tesseract Mod initialized!");
    }
}