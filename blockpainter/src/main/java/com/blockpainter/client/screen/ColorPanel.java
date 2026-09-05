package com.blockpainter.client.screen;

import com.blockpainter.VoxelData;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Compact colour picker: preview swatch, R/G/B/A sliders, hex input, preset and recent palettes.
 * Drawn manually inside the screen; the hex field is a real widget owned by the screen.
 */
public final class ColorPanel {
    public static final int WIDTH = 132;
    public static final int HEIGHT = 138;

    private static final int[] PRESETS = {
            0xFF000000, 0xFF474F52, 0xFF9D9D97, 0xFFF9FFFE, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D, 0xFF80C71F,
            0xFF5E7C16, 0xFF169C9C, 0xFF3AB3DA, 0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA, 0xFF835432
    };
    private static final int SLIDER_H = 10;
    private static final int SLIDER_GAP = 13;
    private static final int SWATCH = 14;

    private final int[] recent = new int[8];
    private int recentCount = 0;

    private int x, y;
    private int color = 0xFFFFFFFF;
    private int draggingSlider = -1;
    private boolean updatingHex = false;
    private TextFieldWidget hexField;
    private final TextRenderer textRenderer;

    public ColorPanel(TextRenderer textRenderer) {
        this.textRenderer = textRenderer;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        if (hexField != null) {
            hexField.setX(x + 34);
            hexField.setY(y + 58);
        }
    }

    public int getX() { return x; }
    public int getY() { return y; }

    /** Creates the hex text field. The screen must add it as a drawable child. */
    public TextFieldWidget createHexField() {
        hexField = new TextFieldWidget(textRenderer, x + 34, y + 58, 74, 14, Text.translatable("blockpainter.ui.hex"));
        hexField.setMaxLength(9);
        hexField.setChangedListener(this::onHexChanged);
        hexField.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("blockpainter.ui.hex_hint")));
        syncHex();
        return hexField;
    }

    public TextFieldWidget hexField() {
        return hexField;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int argb) {
        this.color = argb;
        syncHex();
    }

    public void pushRecent(int argb) {
        for (int i = 0; i < recentCount; i++) {
            if (recent[i] == argb) {
                System.arraycopy(recent, 0, recent, 1, i);
                recent[0] = argb;
                return;
            }
        }
        int n = Math.min(recentCount, recent.length - 1);
        System.arraycopy(recent, 0, recent, 1, n);
        recent[0] = argb;
        recentCount = Math.min(recentCount + 1, recent.length);
    }

    private void syncHex() {
        if (hexField == null) return;
        updatingHex = true;
        hexField.setText(String.format("%02X%02X%02X%02X",
                VoxelData.red(color), VoxelData.green(color), VoxelData.blue(color), VoxelData.alpha(color)));
        updatingHex = false;
    }

    private void onHexChanged(String text) {
        if (updatingHex) return;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) {
                int rgb = Integer.parseInt(s, 16);
                color = (color & 0xFF000000) | (rgb & 0xFFFFFF);
            } else if (s.length() == 8) {
                long v = Long.parseLong(s, 16);
                int r = (int) ((v >> 24) & 0xFF), g = (int) ((v >> 16) & 0xFF), b = (int) ((v >> 8) & 0xFF), a = (int) (v & 0xFF);
                color = VoxelData.argb(a, r, g, b);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    // ---- layout helpers ----

    private int sliderX() { return x + 12; }
    private int sliderW() { return WIDTH - 12; }
    private int sliderY(int i) { return y + 2 + i * SLIDER_GAP; }
    private int presetsY() { return y + 78; }
    private int recentY() { return presetsY() + SWATCH * 2 + 8; }

    private int channel(int i) {
        return switch (i) {
            case 0 -> VoxelData.red(color);
            case 1 -> VoxelData.green(color);
            case 2 -> VoxelData.blue(color);
            default -> VoxelData.alpha(color);
        };
    }

    private void setChannel(int i, int v) {
        v = VoxelData.clamp(v);
        int a = VoxelData.alpha(color), r = VoxelData.red(color), g = VoxelData.green(color), b = VoxelData.blue(color);
        switch (i) {
            case 0 -> r = v;
            case 1 -> g = v;
            case 2 -> b = v;
            default -> a = v;
        }
        color = VoxelData.argb(a, r, g, b);
        syncHex();
    }

    // ---- rendering ----

    public void render(DrawContext ctx, int mouseX, int mouseY) {
        String[] labels = {"R", "G", "B", "A"};
        int[] fills = {0xFFD04040, 0xFF40C040, 0xFF4080FF, 0xFFDDDDDD};
        for (int i = 0; i < 4; i++) {
            int sy = sliderY(i);
            ctx.drawText(textRenderer, labels[i], x + 2, sy + 1, 0xFFCCCCCC, false);
            int sx = sliderX(), sw = sliderW();
            ctx.fill(sx, sy, sx + sw, sy + SLIDER_H, 0xFF202020);
            int fillW = channel(i) * (sw - 2) / 255;
            ctx.fill(sx + 1, sy + 1, sx + 1 + fillW, sy + SLIDER_H - 1, fills[i]);
            int knob = sx + 1 + fillW;
            ctx.fill(knob - 1, sy - 1, knob + 1, sy + SLIDER_H + 1, 0xFFFFFFFF);
            String val = String.valueOf(channel(i));
            ctx.drawText(textRenderer, val, sx + sw - textRenderer.getWidth(val) - 2, sy + 1, 0xFFFFFFFF, true);
        }

        // preview swatch with checker background
        int py = y + 56;
        drawChecker(ctx, x, py, 30, 18);
        ctx.fill(x, py, x + 30, py + 18, color);
        ctx.drawBorder(x, py, 30, 18, 0xFF000000);

        // presets
        int pyy = presetsY();
        ctx.drawText(textRenderer, Text.translatable("blockpainter.ui.palette"), x, pyy - 9, 0xFFAAAAAA, false);
        for (int i = 0; i < PRESETS.length; i++) {
            int sx = x + (i % 8) * (SWATCH + 2);
            int sy = pyy + (i / 8) * (SWATCH + 2);
            ctx.fill(sx, sy, sx + SWATCH, sy + SWATCH, PRESETS[i]);
            boolean hover = mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
            ctx.drawBorder(sx, sy, SWATCH, SWATCH, hover ? 0xFFFFFFFF : 0xFF000000);
        }

        // recent
        int ry = recentY();
        ctx.drawText(textRenderer, Text.translatable("blockpainter.ui.recent"), x, ry - 9, 0xFFAAAAAA, false);
        for (int i = 0; i < recent.length; i++) {
            int sx = x + i * (SWATCH + 2);
            drawChecker(ctx, sx, ry, SWATCH, SWATCH);
            if (i < recentCount) ctx.fill(sx, ry, sx + SWATCH, ry + SWATCH, recent[i]);
            boolean hover = mouseX >= sx && mouseX < sx + SWATCH && mouseY >= ry && mouseY < ry + SWATCH;
            ctx.drawBorder(sx, ry, SWATCH, SWATCH, hover ? 0xFFFFFFFF : 0xFF000000);
        }
    }

    public static void drawChecker(DrawContext ctx, int x, int y, int w, int h) {
        int cell = 4;
        for (int cy = 0; cy < h; cy += cell) {
            for (int cx = 0; cx < w; cx += cell) {
                boolean dark = ((cx / cell) + (cy / cell)) % 2 == 0;
                ctx.fill(x + cx, y + cy, x + Math.min(cx + cell, w), y + Math.min(cy + cell, h), dark ? 0xFF6B6B6B : 0xFF9A9A9A);
            }
        }
    }

    // ---- input ----

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (int i = 0; i < 4; i++) {
            int sy = sliderY(i);
            if (mx >= sliderX() - 2 && mx < sliderX() + sliderW() + 2 && my >= sy - 2 && my < sy + SLIDER_H + 2) {
                draggingSlider = i;
                applySlider(mx);
                return true;
            }
        }
        int pyy = presetsY();
        for (int i = 0; i < PRESETS.length; i++) {
            int sx = x + (i % 8) * (SWATCH + 2);
            int sy = pyy + (i / 8) * (SWATCH + 2);
            if (mx >= sx && mx < sx + SWATCH && my >= sy && my < sy + SWATCH) {
                color = (color & 0xFF000000) | (PRESETS[i] & 0xFFFFFF);
                syncHex();
                return true;
            }
        }
        int ry = recentY();
        for (int i = 0; i < recentCount; i++) {
            int sx = x + i * (SWATCH + 2);
            if (mx >= sx && mx < sx + SWATCH && my >= ry && my < ry + SWATCH) {
                setColor(recent[i]);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (draggingSlider < 0) return false;
        applySlider(mx);
        return true;
    }

    public void mouseReleased() {
        draggingSlider = -1;
    }

    private void applySlider(double mx) {
        double t = (mx - (sliderX() + 1)) / (sliderW() - 2);
        setChannel(draggingSlider, (int) Math.round(Math.max(0, Math.min(1, t)) * 255));
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + WIDTH && my >= y && my < y + HEIGHT;
    }
}
