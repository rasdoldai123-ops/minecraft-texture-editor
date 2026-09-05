package com.blockpainter.client.screen;

import com.blockpainter.VoxelData;
import com.blockpainter.client.BaseTextureLoader;
import com.blockpainter.client.render.PaintedItemRenderer;
import com.blockpainter.item.ItemPaintData;
import com.blockpainter.network.PaintNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/** 2D pixel editor for the texture of the item in hand. */
public class ItemPaintScreen extends AbstractPaintScreen {
    private final Hand hand;
    private final ItemStack stack;
    private int[] pixels;

    private final PixelCanvas canvas = new PixelCanvas();
    private int previewX, previewY, previewSize;

    public ItemPaintScreen(Hand hand, ItemStack stack) {
        super(Text.translatable("blockpainter.ui.title.item", stack.getName()));
        this.hand = hand;
        this.stack = stack;
        int[] existing = ItemPaintData.get(stack);
        this.pixels = existing != null ? existing.clone() : BaseTextureLoader.itemTexture(stack);
    }

    @Override
    protected int[] data() {
        return pixels;
    }

    @Override
    protected void setData(int[] data) {
        pixels = data.clone();
    }

    @Override
    protected boolean supportsBuild() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        int top = contentTop() + 10;
        int bottom = contentBottom();
        int availW = width - ColorPanel.WIDTH - GAP * 3 - 70;
        int cell = Math.max(4, Math.min((bottom - top) / 16, availW / 16));
        canvas.setBounds(GAP + 1, top, cell);

        previewSize = Math.min(64, ColorPanel.WIDTH);
        previewX = canvas.getX() + canvas.getSize() + GAP * 2;
        previewY = top;

        int by = height - BAR_H - GAP;
        int bx = GAP;
        addDrawableChild(ButtonWidget.builder(ui("base"), b -> bulk(() -> pixels = BaseTextureLoader.itemTexture(stack)))
                .dimensions(bx, by, 64, BAR_H).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(ui("base.tip"))).build());
        bx += 66;
        addDrawableChild(ButtonWidget.builder(ui("clear"), b -> bulk(() -> java.util.Arrays.fill(pixels, 0)))
                .dimensions(bx, by, 64, BAR_H).build());
        bx += 66;
        addDrawableChild(ButtonWidget.builder(ui("fill_all"), b -> bulk(() -> java.util.Arrays.fill(pixels, colorPanel.getColor())))
                .dimensions(bx, by, 74, BAR_H).build());
        bx += 76;
        addDrawableChild(ButtonWidget.builder(ui("reset_item"), b -> {
                    ClientPlayNetworking.send(PaintNetworking.PAINT_ITEM, PaintNetworking.writePaintItem(hand, null));
                    close();
                })
                .dimensions(bx, by, 74, BAR_H).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(ui("reset_item.tip"))).build());

        int rx = width - GAP - 60;
        addDrawableChild(ButtonWidget.builder(ui("cancel"), b -> close()).dimensions(rx, by, 60, BAR_H).build());
        rx -= 72;
        addDrawableChild(ButtonWidget.builder(ui("save").copy().formatted(net.minecraft.util.Formatting.GREEN), b -> save())
                .dimensions(rx, by, 70, BAR_H).build());
    }

    private void save() {
        ClientPlayNetworking.send(PaintNetworking.PAINT_ITEM, PaintNetworking.writePaintItem(hand, pixels));
        close();
    }

    // ------------------------------------------------------------------ painting

    private void paintAt(int u, int v, int button) {
        if (u < 0 || v < 0 || u >= 16 || v >= 16) return;
        int color = colorPanel.getColor();
        PaintTool t = tool;
        if (button == 1) t = PaintTool.ERASER;

        switch (t) {
            case PICKER -> {
                int c = pixels[VoxelData.pixelIndex(u, v)];
                if (!isEmptyColor(c)) colorPanel.setColor(c);
            }
            case FILL -> {
                beginStroke();
                floodFill(u, v,
                        (a, b) -> pixels[VoxelData.pixelIndex(a, b)],
                        (a, b) -> {
                            pixels[VoxelData.pixelIndex(a, b)] = color;
                            if (mirrorX) pixels[VoxelData.pixelIndex(15 - a, b)] = color;
                            return color;
                        });
            }
            default -> {
                beginStroke();
                int value = t == PaintTool.ERASER ? 0 : color;
                int off = -(brushSize - 1) / 2;
                for (int dv = 0; dv < brushSize; dv++) {
                    for (int du = 0; du < brushSize; du++) {
                        int cu = u + du + off, cv = v + dv + off;
                        if (cu < 0 || cv < 0 || cu >= 16 || cv >= 16) continue;
                        pixels[VoxelData.pixelIndex(cu, cv)] = value;
                        if (mirrorX) pixels[VoxelData.pixelIndex(15 - cu, cv)] = value;
                    }
                }
            }
        }
    }

    @Override
    protected boolean handleContentClick(double mouseX, double mouseY, int button) {
        if (canvas.contains(mouseX, mouseY) && (button == 0 || button == 1)) {
            strokeButton = button;
            paintAt(canvas.cellU(mouseX), canvas.cellV(mouseY), button);
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleContentDrag(double mouseX, double mouseY, int button, double dx, double dy) {
        if (strokeActive && canvas.contains(mouseX, mouseY)) {
            paintAt(canvas.cellU(mouseX), canvas.cellV(mouseY), strokeButton);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawText(textRenderer, ui("canvas.item"), canvas.getX(), canvas.getY() - 10, 0xFFAAAAAA, false);
        canvas.render(ctx, mouseX, mouseY, (u, v) -> pixels[VoxelData.pixelIndex(u, v)], brushSize, mirrorX);

        // live preview using the same renderer as in-world
        ctx.drawText(textRenderer, ui("preview"), previewX, previewY - 10, 0xFFAAAAAA, false);
        ColorPanel.drawChecker(ctx, previewX, previewY, previewSize, previewSize);
        ctx.drawBorder(previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, 0xFF000000);
        ctx.draw();

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(previewX, previewY + previewSize, 150);
        ms.scale(previewSize, -previewSize, previewSize);
        PaintedItemRenderer.render(pixels, ms, ctx.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        ctx.draw();
        ms.pop();

        // the original item for reference
        int iy = previewY + previewSize + 14;
        ctx.drawText(textRenderer, ui("original"), previewX, iy - 10, 0xFFAAAAAA, false);
        ctx.drawItem(stack, previewX, iy);
    }

    @Override
    protected Text statusHint() {
        return ui("hint.item");
    }
}
