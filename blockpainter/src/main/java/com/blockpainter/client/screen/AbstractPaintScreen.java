package com.blockpainter.client.screen;

import com.blockpainter.VoxelData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Shared editor chrome: tool bar, brush size, mirror, undo/redo, colour panel and save/cancel.
 */
public abstract class AbstractPaintScreen extends Screen {
    protected static final int TOP_BAR_Y = 4;
    protected static final int BAR_H = 20;
    protected static final int GAP = 6;

    protected PaintTool tool = PaintTool.PENCIL;
    protected int brushSize = 1;
    protected boolean mirrorX = false;

    protected final History history = new History();
    protected ColorPanel colorPanel;
    protected TextFieldWidget hexField;

    protected final Map<PaintTool, ButtonWidget> toolButtons = new EnumMap<>(PaintTool.class);
    protected final ButtonWidget[] sizeButtons = new ButtonWidget[3];
    protected ButtonWidget mirrorButton, undoButton, redoButton;

    protected boolean strokeActive = false;
    protected int strokeButton = 0;

    protected AbstractPaintScreen(Text title) {
        super(title);
    }

    protected static Text ui(String key) {
        return Text.translatable("blockpainter.ui." + key);
    }

    /** Working data (pixels or voxels) used for undo snapshots. */
    protected abstract int[] data();

    protected abstract void setData(int[] data);

    protected abstract boolean supportsBuild();

    /** Called whenever the data changes so subclasses can refresh caches. */
    protected void onDataChanged() {}

    @Override
    protected void init() {
        if (colorPanel == null) colorPanel = new ColorPanel(textRenderer);

        int bx = GAP;
        int by = TOP_BAR_Y;
        for (PaintTool t : PaintTool.values()) {
            if (t == PaintTool.BUILD && !supportsBuild()) continue;
            int w = 44;
            ButtonWidget b = ButtonWidget.builder(ui("tool." + t.key), btn -> selectTool(t))
                    .dimensions(bx, by, w, BAR_H)
                    .tooltip(Tooltip.of(ui("tool." + t.key + ".tip")))
                    .build();
            toolButtons.put(t, b);
            addDrawableChild(b);
            bx += w + 2;
        }
        bx += GAP;
        for (int i = 0; i < 3; i++) {
            final int size = i + 1;
            ButtonWidget b = ButtonWidget.builder(Text.literal(String.valueOf(size)), btn -> selectSize(size))
                    .dimensions(bx, by, 16, BAR_H)
                    .tooltip(Tooltip.of(ui("size.tip")))
                    .build();
            sizeButtons[i] = b;
            addDrawableChild(b);
            bx += 17;
        }
        bx += GAP;
        mirrorButton = ButtonWidget.builder(ui("mirror"), btn -> {
                    mirrorX = !mirrorX;
                    refreshButtons();
                })
                .dimensions(bx, by, 56, BAR_H)
                .tooltip(Tooltip.of(ui("mirror.tip")))
                .build();
        addDrawableChild(mirrorButton);
        bx += 58 + GAP;
        undoButton = ButtonWidget.builder(ui("undo"), btn -> undo()).dimensions(bx, by, 40, BAR_H)
                .tooltip(Tooltip.of(Text.literal("Ctrl+Z"))).build();
        addDrawableChild(undoButton);
        bx += 42;
        redoButton = ButtonWidget.builder(ui("redo"), btn -> redo()).dimensions(bx, by, 40, BAR_H)
                .tooltip(Tooltip.of(Text.literal("Ctrl+Y"))).build();
        addDrawableChild(redoButton);

        colorPanel.setPosition(width - ColorPanel.WIDTH - GAP, contentTop());
        hexField = colorPanel.createHexField();
        addDrawableChild(hexField);

        refreshButtons();
    }

    protected int contentTop() {
        return TOP_BAR_Y + BAR_H + GAP;
    }

    protected int contentBottom() {
        return height - BAR_H - GAP - 12;
    }

    protected void selectTool(PaintTool t) {
        tool = t;
        refreshButtons();
    }

    protected void selectSize(int size) {
        brushSize = size;
        refreshButtons();
    }

    protected void refreshButtons() {
        for (Map.Entry<PaintTool, ButtonWidget> e : toolButtons.entrySet()) {
            boolean active = e.getKey() == tool;
            e.getValue().setMessage(active
                    ? ui("tool." + e.getKey().key).copy().formatted(Formatting.YELLOW)
                    : ui("tool." + e.getKey().key));
        }
        for (int i = 0; i < 3; i++) {
            boolean active = brushSize == i + 1;
            sizeButtons[i].setMessage(active
                    ? Text.literal(String.valueOf(i + 1)).formatted(Formatting.YELLOW)
                    : Text.literal(String.valueOf(i + 1)));
        }
        mirrorButton.setMessage(mirrorX ? ui("mirror").copy().formatted(Formatting.YELLOW) : ui("mirror"));
        undoButton.active = history.canUndo();
        redoButton.active = history.canRedo();
    }

    // ------------------------------------------------------------------ history

    protected void beginStroke() {
        if (strokeActive) return;
        strokeActive = true;
        history.push(data());
        if (tool == PaintTool.PENCIL || tool == PaintTool.FILL || tool == PaintTool.BUILD) {
            colorPanel.pushRecent(colorPanel.getColor());
        }
        refreshButtons();
    }

    protected void endStroke() {
        strokeActive = false;
    }

    protected void undo() {
        int[] prev = history.undo(data());
        if (prev != null) {
            setData(prev);
            onDataChanged();
        }
        refreshButtons();
    }

    protected void redo() {
        int[] next = history.redo(data());
        if (next != null) {
            setData(next);
            onDataChanged();
        }
        refreshButtons();
    }

    /** Wraps a bulk operation (clear/fill/base) in a history entry. */
    protected void bulk(Runnable op) {
        history.push(data());
        op.run();
        onDataChanged();
        refreshButtons();
    }

    // ------------------------------------------------------------------ 2D helpers

    /** Flood fill on an abstract 16x16 grid via callbacks. */
    protected static void floodFill(int startU, int startV, java.util.function.IntBinaryOperator get,
                                    java.util.function.IntBinaryOperator set) {
        int target = get.applyAsInt(startU, startV);
        int replacement = set.applyAsInt(startU, startV);
        if (target == replacement) return;
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        boolean[] seen = new boolean[VoxelData.AREA];
        stack.push(new int[]{startU, startV});
        seen[VoxelData.pixelIndex(startU, startV)] = true;
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int u = p[0], v = p[1];
            int[][] n = {{u + 1, v}, {u - 1, v}, {u, v + 1}, {u, v - 1}};
            for (int[] q : n) {
                if (q[0] < 0 || q[1] < 0 || q[0] >= 16 || q[1] >= 16) continue;
                int idx = VoxelData.pixelIndex(q[0], q[1]);
                if (seen[idx]) continue;
                if (get.applyAsInt(q[0], q[1]) != target) continue;
                seen[idx] = true;
                set.applyAsInt(q[0], q[1]);
                stack.push(q);
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        ctx.fill(0, 0, width, height, 0xC0101014);
        ctx.drawText(textRenderer, title, width - textRenderer.getWidth(title) - GAP, TOP_BAR_Y + 6, 0xFFE0E0E0, true);

        renderContent(ctx, mouseX, mouseY, delta);

        ctx.drawText(textRenderer, ui("color"), colorPanel.getX(), colorPanel.getY() - 10, 0xFFAAAAAA, false);
        colorPanel.render(ctx, mouseX, mouseY);

        super.render(ctx, mouseX, mouseY, delta);

        Text hint = statusHint();
        if (hint != null) {
            ctx.drawText(textRenderer, hint, GAP, height - 10, 0xFF9A9AA0, false);
        }
    }

    protected abstract void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta);

    protected Text statusHint() {
        return ui("hint.common");
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (colorPanel.mouseClicked(mouseX, mouseY, button)) {
            setFocused(null);
            return true;
        }
        if (handleContentClick(mouseX, mouseY, button)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected abstract boolean handleContentClick(double mouseX, double mouseY, int button);

    protected abstract boolean handleContentDrag(double mouseX, double mouseY, int button, double dx, double dy);

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (colorPanel.mouseDragged(mouseX, mouseY)) return true;
        if (handleContentDrag(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        colorPanel.mouseReleased();
        endStroke();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hexField != null && hexField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (hasShiftDown()) redo(); else undo();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_1 -> { selectSize(1); return true; }
            case GLFW.GLFW_KEY_2 -> { selectSize(2); return true; }
            case GLFW.GLFW_KEY_3 -> { selectSize(3); return true; }
            case GLFW.GLFW_KEY_B -> { selectTool(PaintTool.PENCIL); return true; }
            case GLFW.GLFW_KEY_E -> { selectTool(PaintTool.ERASER); return true; }
            case GLFW.GLFW_KEY_G -> { selectTool(PaintTool.FILL); return true; }
            case GLFW.GLFW_KEY_I -> { selectTool(PaintTool.PICKER); return true; }
            case GLFW.GLFW_KEY_M -> { mirrorX = !mirrorX; refreshButtons(); return true; }
            default -> {}
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected boolean isEmptyColor(int c) {
        return VoxelData.isEmpty(c);
    }
}
