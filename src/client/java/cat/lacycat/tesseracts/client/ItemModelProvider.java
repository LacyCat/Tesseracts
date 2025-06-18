package cat.lacycat.tesseracts.client;

import cat.lacycat.tesseracts.TesseractMod;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.Model;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class ItemModelProvider extends FabricModelProvider {

    public ItemModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {

    }

    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {
        Model builtinEntityModel = new Model(Optional.of(new Identifier("builtin/entity")), Optional.empty());
        itemModelGenerator.register(TesseractMod.TESSERACT_ITEM, builtinEntityModel);
    }
}
