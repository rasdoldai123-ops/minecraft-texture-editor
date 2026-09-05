package com.blockpainter.mixin;

import com.blockpainter.client.render.PaintedItemRenderer;
import com.blockpainter.item.ItemPaintData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the visual of any item stack that carries a painted texture, keeping the item itself untouched.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void blockpainter$renderPainted(ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
                                            MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                            int light, int overlay, BakedModel model, CallbackInfo ci) {
        int[] pixels = ItemPaintData.get(stack);
        if (pixels == null) return;
        ci.cancel();

        matrices.push();
        model.getTransformation().getTransformation(mode).apply(leftHanded, matrices);
        matrices.translate(-0.5f, -0.5f, -0.5f);
        PaintedItemRenderer.render(pixels, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }
}
