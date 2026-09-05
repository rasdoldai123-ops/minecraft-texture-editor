package com.blockpainter.client.screen;

import com.blockpainter.VoxelData;
import net.minecraft.client.gui.DrawContext;

/** A 16x16 grid of paintable cells. The data source is provided by the owning screen. */
public final class PixelCanvas {
    public interface Source {
        int color(int u, int v);
        /** Colour drawn faded behind the main layer (onion skin), or 0 for none. */
        default int ghost(int u, int v) { return 0; }
    }

    private int x, y, cell;

    public void setBounds(int x, int y, int cell) {
        this.x = x;
        this.y = y;
        this.cell = cell;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getCell() { return cell; }
    public int getSize() { return cell * VoxelData.SIZE; }

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + getSize() && my < y + getSize();
    }

    public int cellU(double mx) {
        return (int) Math.floor((mx - x) / cell);
    }

    public int cellV(double my) {
        return (int) Math.floor((my - y) / cell);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, Source source, int brushSize, boolean mirrorX) {
        int size = getSize();
        ColorPanel.drawChecker(ctx, x, y, size, size);

        for (int v = 0; v < VoxelData.SIZE; v++) {
            for (int u = 0; u < VoxelData.SIZE; u++) {
                int px = x + u * cell, py = y + v * cell;
                int ghost = source.ghost(u, v);
                if (!VoxelData.isEmpty(ghost)) {
                    int faded = (Math.min(90, VoxelData.alpha(ghost)) << 24) | (ghost & 0xFFFFFF);
                    ctx.fill(px, py, px + cell, py + cell, faded);
                }
                int c = source.color(u, v);
                if (!VoxelData.isEmpty(c)) {
                    ctx.fill(px, py, px + cell, py + cell, c);
                }
            }
        }

        // grid lines
        if (cell >= 6) {
            for (int i = 0; i <= VoxelData.SIZE; i++) {
                int lineColor = (i % 4 == 0) ? 0x60000000 : 0x28000000;
                ctx.fill(x + i * cell, y, x + i * cell + 1, y + size, lineColor);
                ctx.fill(x, y + i * cell, x + size, y + i * cell + 1, lineColor);
            }
        }

        // mirror axis
        if (mirrorX) {
            ctx.fill(x + size / 2, y, x + size / 2 + 1, y + size, 0xA0FF4040);
        }

        // hover / brush preview
        if (contains(mouseX, mouseY)) {
            int u = cellU(mouseX), v = cellV(mouseY);
            int off = -(brushSize - 1) / 2;
            for (int dv = 0; dv < brushSize; dv++) {
                for (int du = 0; du < brushSize; du++) {
                    int cu = u + du + off, cv = v + dv + off;
                    if (cu < 0 || cv < 0 || cu >= VoxelData.SIZE || cv >= VoxelData.SIZE) continue;
                    ctx.drawBorder(x + cu * cell, y + cv * cell, cell, cell, 0xFFFFFFFF);
                    if (mirrorX) {
                        ctx.drawBorder(x + (15 - cu) * cell, y + cv * cell, cell, cell, 0xA0FFFFFF);
                    }
                }
            }
        }

        ctx.drawBorder(x - 1, y - 1, size + 2, size + 2, 0xFF000000);
    }
}
