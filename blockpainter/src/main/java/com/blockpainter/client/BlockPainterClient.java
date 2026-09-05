package com.blockpainter.client;

import com.blockpainter.BlockPainterMod;
import com.blockpainter.client.render.PaintedBlockRenderer;
import com.blockpainter.client.screen.BlockPaintScreen;
import com.blockpainter.client.screen.ItemPaintScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class BlockPainterClient implements ClientModInitializer {
    public static KeyBinding OPEN_EDITOR;

    @Override
    public void onInitializeClient() {
        OPEN_EDITOR = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockpainter.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.blockpainter"
        ));

        BlockEntityRendererRegistry.register(BlockPainterMod.PAINTED_BLOCK_ENTITY, PaintedBlockRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_EDITOR.wasPressed()) {
                openEditor(client);
            }
        });
    }

    private static void openEditor(MinecraftClient client) {
        if (client.player == null || client.world == null || client.currentScreen != null) return;

        ItemStack main = client.player.getMainHandStack();
        if (!main.isEmpty()) {
            client.setScreen(new ItemPaintScreen(Hand.MAIN_HAND, main.copy()));
            return;
        }

        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            client.setScreen(new BlockPaintScreen(blockHit.getBlockPos()));
            return;
        }

        client.player.sendMessage(Text.translatable("blockpainter.msg.nothing"), true);
    }
}
