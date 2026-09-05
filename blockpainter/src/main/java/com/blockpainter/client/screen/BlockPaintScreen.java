package com.blockpainter.client.screen;

import com.blockpainter.VoxelData;
import com.blockpainter.block.PaintedBlockEntity;
import com.blockpainter.client.BaseTextureLoader;
import com.blockpainter.network.PaintNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Arrays;

/** 3D voxel editor for a block in the world. */
public class BlockPaintScreen extends AbstractPaintScreen {
    private final BlockPos pos;
    private final BlockState originalState;
    private final boolean alreadyPainted;
    private int[] voxels;
    private int version = 0;

    private final VoxelView view = new VoxelView();
    private final PixelCanvas canvas = new PixelCanvas();

    private ButtonWidget[] axisButtons = new ButtonWidget[3];
    private ButtonWidget cutawayButton;
    private ButtonWidget restoreButton;

    public BlockPaintScreen(BlockPos pos) {
        super(Text.translatable("blockpainter.ui.title.block"));
        this.pos = pos;

        MinecraftClient client = MinecraftClient.getInstance();
        BlockEntity be = client.world != null ? client.world.getBlockEntity(pos) : null;
        if (be instanceof PaintedBlockEntity painted) {
            alreadyPainted = true;
            originalState = painted.getOriginalState();
            voxels = painted.getVoxels().clone();
        } else {
            alreadyPainted = false;
            originalState = client.world != null ? client.world.getBlockState(pos) : null;
            voxels = originalState != null ? BaseTextureLoader.blockVoxels(originalState) : new int[VoxelData.VOLUME];
        }
    }

    @Override
    protected int[] data() {
        return voxels;
    }

    @Override
    protected void setData(int[] data) {
        voxels = data.clone();
    }

    @Override
    protected boolean supportsBuild() {
        return true;
    }

    @Override
    protected void onDataChanged() {
        version++;
        view.invalidate();
    }

    @Override
    protected void init() {
        super.init();

        int headerY = contentTop();
        int top = headerY + BAR_H + 12;
        int bottom = contentBottom();
        int availH = bottom - top;
        int availW = width - ColorPanel.WIDTH - GAP * 4;

        int cell = Math.max(4, Math.min(availH / 16, (availW - GAP) / 32));
        int viewSize = Math.min(availH, availW - cell * 16 - GAP);
        viewSize = Math.max(viewSize, 80);

        view.setBounds(GAP + 1, top, viewSize);
        canvas.setBounds(view.getX() + viewSize + GAP + 1, top, cell);

        // header: axis / layer / cutaway
        int hx = GAP;
        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            final int axis = i;
            axisButtons[i] = ButtonWidget.builder(Text.literal(axes[i]), b -> setAxis(axis))
                    .dimensions(hx, headerY, 20, BAR_H)
                    .tooltip(Tooltip.of(ui("axis.tip")))
                    .build();
            addDrawableChild(axisButtons[i]);
            hx += 21;
        }
        hx += GAP;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> setLayer(view.sliceLayer - 1))
                .dimensions(hx, headerY, 16, BAR_H).build());
        hx += 16 + 58;
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> setLayer(view.sliceLayer + 1))
                .dimensions(hx, headerY, 16, BAR_H).build());
        hx += 18 + GAP;
        cutawayButton = ButtonWidget.builder(ui("cutaway"), b -> {
                    view.cutaway = !view.cutaway;
                    view.invalidate();
                    refreshHeader();
                })
                .dimensions(hx, headerY, 60, BAR_H)
                .tooltip(Tooltip.of(ui("cutaway.tip")))
                .build();
        addDrawableChild(cutawayButton);

        // bottom bar
        int by = height - BAR_H - GAP;
        int bx = GAP;
        addDrawableChild(ButtonWidget.builder(ui("base"), b -> bulk(() -> {
                    if (originalState != null) voxels = BaseTextureLoader.blockVoxels(originalState);
                }))
                .dimensions(bx, by, 64, BAR_H).tooltip(Tooltip.of(ui("base.tip"))).build());
        bx += 66;
        addDrawableChild(ButtonWidget.builder(ui("clear"), b -> bulk(() -> Arrays.fill(voxels, 0)))
                .dimensions(bx, by, 64, BAR_H).build());
        bx += 66;
        addDrawableChild(ButtonWidget.builder(ui("fill_all"), b -> bulk(() -> Arrays.fill(voxels, colorPanel.getColor())))
                .dimensions(bx, by, 74, BAR_H).build());
        bx += 76;
        addDrawableChild(ButtonWidget.builder(ui("hollow"), b -> bulk(this::hollow))
                .dimensions(bx, by, 64, BAR_H).tooltip(Tooltip.of(ui("hollow.tip"))).build());
        bx += 66;
        restoreButton = ButtonWidget.builder(ui("restore_block"), b -> {
                    ClientPlayNetworking.send(PaintNetworking.RESTORE_BLOCK, PaintNetworking.writeRestoreBlock(pos));
                    close();
                })
                .dimensions(bx, by, 80, BAR_H).tooltip(Tooltip.of(ui("restore_block.tip"))).build();
        restoreButton.active = alreadyPainted;
        addDrawableChild(restoreButton);

        int rx = width - GAP - 60;
        addDrawableChild(ButtonWidget.builder(ui("cancel"), b -> close()).dimensions(rx, by, 60, BAR_H).build());
        rx -= 72;
        addDrawableChild(ButtonWidget.builder(ui("save").copy().formatted(Formatting.GREEN), b -> save())
                .dimensions(rx, by, 70, BAR_H).build());

        refreshHeader();
    }

    private void save() {
        ClientPlayNetworking.send(PaintNetworking.PAINT_BLOCK, PaintNetworking.writePaintBlock(pos, voxels));
        close();
    }

    private void hollow() {
        int[] copy = voxels.clone();
        for (int y = 1; y < 15; y++)
            for (int z = 1; z < 15; z++)
                for (int x = 1; x < 15; x++) {
                    boolean enclosed = true;
                    for (Direction d : Direction.values()) {
                        if (VoxelData.isEmpty(copy, x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ())) {
                            enclosed = false;
                            break;
                        }
                    }
                    if (enclosed) voxels[VoxelData.index(x, y, z)] = 0;
                }
    }

    private void setAxis(int axis) {
        view.sliceAxis = axis;
        view.invalidate();
        refreshHeader();
    }

    private void setLayer(int layer) {
        view.sliceLayer = Math.max(0, Math.min(15, layer));
        view.invalidate();
        refreshHeader();
    }

    private void refreshHeader() {
        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            axisButtons[i].setMessage(i == view.sliceAxis
                    ? Text.literal(axes[i]).formatted(Formatting.YELLOW)
                    : Text.literal(axes[i]));
        }
        cutawayButton.setMessage(view.cutaway ? ui("cutaway").copy().formatted(Formatting.YELLOW) : ui("cutaway"));
    }

    // ------------------------------------------------------------------ slice mapping

    private int vx(int u, int v) {
        return switch (view.sliceAxis) {
            case 0 -> view.sliceLayer;
            default -> u;
        };
    }

    private int vy(int u, int v) {
        return switch (view.sliceAxis) {
            case 1 -> view.sliceLayer;
            default -> 15 - v;
        };
    }

    private int vz(int u, int v) {
        return switch (view.sliceAxis) {
            case 0 -> 15 - u;
            case 1 -> v;
            default -> view.sliceLayer;
        };
    }

    private int sliceColor(int u, int v, int layerOffset) {
        int x = vx(u, v), y = vy(u, v), z = vz(u, v);
        switch (view.sliceAxis) {
            case 0 -> x += layerOffset;
            case 1 -> y += layerOffset;
            default -> z += layerOffset;
        }
        if (!VoxelData.inBounds(x, y, z)) return 0;
        return voxels[VoxelData.index(x, y, z)];
    }

    private void setVoxel(int x, int y, int z, int color) {
        if (!VoxelData.inBounds(x, y, z)) return;
        voxels[VoxelData.index(x, y, z)] = color;
        if (mirrorX) voxels[VoxelData.index(15 - x, y, z)] = color;
    }

    // ------------------------------------------------------------------ painting: 2D slice

    private void paintSlice(int u, int v, int button) {
        if (u < 0 || v < 0 || u >= 16 || v >= 16) return;
        int color = colorPanel.getColor();
        PaintTool t = tool;
        if (button == 1) t = PaintTool.ERASER;
        if (t == PaintTool.BUILD) t = PaintTool.PENCIL;

        switch (t) {
            case PICKER -> {
                int c = sliceColor(u, v, 0);
                if (!isEmptyColor(c)) colorPanel.setColor(c);
            }
            case FILL -> {
                beginStroke();
                floodFill(u, v,
                        (a, b) -> sliceColor(a, b, 0),
                        (a, b) -> {
                            setVoxel(vx(a, b), vy(a, b), vz(a, b), color);
                            return color;
                        });
                onDataChanged();
            }
            default -> {
                beginStroke();
                int value = t == PaintTool.ERASER ? 0 : color;
                int off = -(brushSize - 1) / 2;
                for (int dv = 0; dv < brushSize; dv++) {
                    for (int du = 0; du < brushSize; du++) {
                        int cu = u + du + off, cv = v + dv + off;
                        if (cu < 0 || cv < 0 || cu >= 16 || cv >= 16) continue;
                        setVoxel(vx(cu, cv), vy(cu, cv), vz(cu, cv), value);
                    }
                }
                onDataChanged();
            }
        }
    }

    // ------------------------------------------------------------------ painting: 3D view

    private void paint3D(int button) {
        if (!view.hasHit) return;
        int color = colorPanel.getColor();
        PaintTool t = tool;
        if (button == 1) t = PaintTool.ERASER;

        Direction face = view.hitFace;
        int hx = view.hitX, hy = view.hitY, hz = view.hitZ;

        switch (t) {
            case PICKER -> {
                int c = voxels[VoxelData.index(hx, hy, hz)];
                if (!isEmptyColor(c)) colorPanel.setColor(c);
                return;
            }
            case FILL -> {
                if (view.planeHit) return;
                beginStroke();
                floodFace(hx, hy, hz, face, color);
            }
            case BUILD -> {
                beginStroke();
                if (view.planeHit) {
                    brushOnPlane(hx, hy, hz, face, color, true);
                } else {
                    int nx = hx + face.getOffsetX(), ny = hy + face.getOffsetY(), nz = hz + face.getOffsetZ();
                    if (VoxelData.inBounds(nx, ny, nz)) brushOnPlane(nx, ny, nz, face, color, true);
                }
            }
            case ERASER -> {
                beginStroke();
                brushOnPlane(hx, hy, hz, face, 0, false);
            }
            default -> {
                beginStroke();
                brushOnPlane(hx, hy, hz, face, color, view.planeHit);
            }
        }
        onDataChanged();
        syncLayerToHit();
    }

    /** Moves the editing slice to the voxel that was just clicked so the 2D canvas follows the 3D view. */
    private void syncLayerToHit() {
        int along = switch (view.sliceAxis) {
            case 0 -> view.hitX;
            case 1 -> view.hitY;
            default -> view.hitZ;
        };
        if (view.sliceLayer != along) {
            view.sliceLayer = along;
            view.invalidate();
        }
    }

    /**
     * Applies the brush in the plane perpendicular to {@code normal} around (cx,cy,cz).
     * When {@code createNew} is false only existing voxels are recoloured (painting on a surface).
     */
    private void brushOnPlane(int cx, int cy, int cz, Direction normal, int color, boolean createNew) {
        Direction.Axis n = normal.getAxis();
        int off = -(brushSize - 1) / 2;
        for (int i = 0; i < brushSize; i++) {
            for (int j = 0; j < brushSize; j++) {
                int a = i + off, b = j + off;
                int x = cx, y = cy, z = cz;
                switch (n) {
                    case X -> { y += a; z += b; }
                    case Y -> { x += a; z += b; }
                    case Z -> { x += a; y += b; }
                }
                if (!VoxelData.inBounds(x, y, z)) continue;
                if (!createNew && VoxelData.isEmpty(voxels[VoxelData.index(x, y, z)])) continue;
                setVoxel(x, y, z, color);
            }
        }
    }

    /** Flood fills connected voxels of the same colour that are exposed on the given face. */
    private void floodFace(int sx, int sy, int sz, Direction face, int color) {
        int target = voxels[VoxelData.index(sx, sy, sz)];
        if (target == color) return;
        Direction.Axis n = face.getAxis();
        boolean[] seen = new boolean[VoxelData.VOLUME];
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy, sz});
        seen[VoxelData.index(sx, sy, sz)] = true;
        setVoxel(sx, sy, sz, color);

        int[][] offsets = switch (n) {
            case X -> new int[][]{{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            case Y -> new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
            case Z -> new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        };

        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            for (int[] o : offsets) {
                int x = p[0] + o[0], y = p[1] + o[1], z = p[2] + o[2];
                if (!VoxelData.inBounds(x, y, z)) continue;
                int idx = VoxelData.index(x, y, z);
                if (seen[idx]) continue;
                if (voxels[idx] != target) continue;
                // must be exposed on the same face
                if (!VoxelData.isEmpty(voxels, x + face.getOffsetX(), y + face.getOffsetY(), z + face.getOffsetZ())) continue;
                seen[idx] = true;
                setVoxel(x, y, z, color);
                stack.push(new int[]{x, y, z});
            }
        }
    }

    // ------------------------------------------------------------------ input

    private boolean rotating = false;

    @Override
    protected boolean handleContentClick(double mouseX, double mouseY, int button) {
        if (canvas.contains(mouseX, mouseY) && (button == 0 || button == 1)) {
            strokeButton = button;
            paintSlice(canvas.cellU(mouseX), canvas.cellV(mouseY), button);
            return true;
        }
        if (view.contains(mouseX, mouseY)) {
            if (button == 2 || (button == 1 && hasShiftDown())) {
                rotating = true;
                return true;
            }
            if (button == 0 || button == 1) {
                strokeButton = button;
                view.pick(voxels, mouseX, mouseY);
                paint3D(button);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean handleContentDrag(double mouseX, double mouseY, int button, double dx, double dy) {
        if (rotating) {
            view.rotate(dx, dy);
            return true;
        }
        if (strokeActive) {
            if (canvas.contains(mouseX, mouseY)) {
                paintSlice(canvas.cellU(mouseX), canvas.cellV(mouseY), strokeButton);
                return true;
            }
            if (view.contains(mouseX, mouseY)) {
                view.pick(voxels, mouseX, mouseY);
                if (view.hasHit && (tool == PaintTool.PENCIL || tool == PaintTool.ERASER || strokeButton == 1)) {
                    paint3D(strokeButton);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        rotating = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (view.contains(mouseX, mouseY)) {
            if (hasShiftDown()) {
                setLayer(view.sliceLayer + (amount > 0 ? 1 : -1));
            } else {
                view.zoom(amount);
            }
            return true;
        }
        if (canvas.contains(mouseX, mouseY)) {
            setLayer(view.sliceLayer + (amount > 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hexField == null || !hexField.isFocused()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_DOWN -> { setLayer(view.sliceLayer - 1); return true; }
                case GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_UP -> { setLayer(view.sliceLayer + 1); return true; }
                case GLFW.GLFW_KEY_X -> { setAxis(0); return true; }
                case GLFW.GLFW_KEY_C -> { setAxis(1); return true; }
                case GLFW.GLFW_KEY_V -> { setAxis(2); return true; }
                case GLFW.GLFW_KEY_H -> { view.cutaway = !view.cutaway; view.invalidate(); refreshHeader(); return true; }
                case GLFW.GLFW_KEY_N -> { selectTool(PaintTool.BUILD); return true; }
                default -> {}
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // layer label in the header
        String[] axes = {"X", "Y", "Z"};
        Text layerText = ui("layer").copy().append(" " + axes[view.sliceAxis] + " = " + view.sliceLayer);
        ctx.drawText(textRenderer, layerText, GAP + 63 + 18, contentTop() + 6, 0xFFE0E0E0, true);

        if (!rotating && !strokeActive) {
            view.pick(voxels, mouseX, mouseY);
        }
        ctx.drawText(textRenderer, ui("view3d"), view.getX(), view.getY() - 10, 0xFFAAAAAA, false);
        view.render(ctx, voxels, version + (view.cutaway ? 100000 : 0) + view.sliceLayer * 1000 + view.sliceAxis * 100);

        Text sliceTitle = ui("canvas.slice." + axes[view.sliceAxis].toLowerCase());
        ctx.drawText(textRenderer, sliceTitle, canvas.getX(), canvas.getY() - 10, 0xFFAAAAAA, false);
        canvas.render(ctx, mouseX, mouseY, new PixelCanvas.Source() {
            @Override
            public int color(int u, int v) {
                return sliceColor(u, v, 0);
            }

            @Override
            public int ghost(int u, int v) {
                return sliceColor(u, v, -1);
            }
        }, brushSize, mirrorX);

        if (originalState != null) {
            Text name = originalState.getBlock().getName();
            ctx.drawText(textRenderer, name, view.getX(), view.getY() + view.getSize() + 3, 0xFF9A9AA0, false);
        }
    }

    @Override
    protected Text statusHint() {
        return ui("hint.block");
    }
}
