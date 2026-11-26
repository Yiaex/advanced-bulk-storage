package dev.advancedbulkstorage.client;

import dev.advancedbulkstorage.BulkStorageMod;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class BulkStorageJei implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return BulkStorageMod.id("filter_editor");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterEditorScreen.class, new IGhostIngredientHandler<>() {
            @Override
            public <I> List<Target<I>> getTargetsTyped(FilterEditorScreen screen, ITypedIngredient<I> ingredient,
                                                       boolean doStart) {
                var item = ingredient.getItemStack();
                var area = screen.ghostArea();
                if (item.isEmpty() || area == null) {
                    return List.of();
                }
                ItemStack stack = item.orElseThrow();
                return List.of(new Target<I>() {
                    @Override
                    public Rect2i getArea() { return area; }
                    @Override
                    public void accept(I ignored) { screen.inspectIngredient(stack); }
                });
            }

            @Override
            public void onComplete() {}
        });
    }
}
