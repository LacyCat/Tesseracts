package cat.lacycat.tesseracts.client;

import cat.lacycat.tesseracts.TesseractMod;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.data.server.recipe.ShapelessRecipeJsonBuilder;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory; // 이 import 추가

import java.util.function.Consumer;

public class RecipeProvider extends FabricRecipeProvider {
    public RecipeProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate(Consumer<RecipeJsonProvider> consumer) {
        // 일반 조합 레시피 - RecipeCategory 추가
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, TesseractMod.TESSERACT_ITEM, 1)
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .input('A', Items.DIAMOND)
                .input('B', Items.ENDER_PEARL)
                .input('C', Items.NETHER_STAR)
                .criterion(hasItem(Items.NETHER_STAR), conditionsFromItem(Items.NETHER_STAR))
                .offerTo(consumer);
    }
}