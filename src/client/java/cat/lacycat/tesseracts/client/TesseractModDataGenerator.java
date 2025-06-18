package cat.lacycat.tesseracts.client;

import cat.lacycat.tesseracts.TesseractMod;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.client.render.model.json.ItemModelGenerator;

public class TesseractModDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        pack.addProvider(ItemModelProvider::new);
        pack.addProvider(RecipeProvider::new);
    }

}
