package dev.packweaver.bridge.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：F12 呼出游戏内多窗口叠加层（规划书第 13.1 / 扩展 D 章）。
 *
 * - F12：切换叠加层显示/隐藏
 * - Shift+F12：打开窗口布局编辑界面（拖拽移动、显示/隐藏各窗口）
 */
public class PackWeaverBridgeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyBinding overlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.packweaver.toggle_overlay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F12, "category.packweaver"));

        HudRenderCallback.EVENT.register(OverlayRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (overlayKey.wasPressed()) {
                boolean shift = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);
                if (shift) {
                    if (client.currentScreen == null) {
                        client.setScreen(new OverlayScreen());
                    }
                } else {
                    OverlayManager.getInstance().toggleVisible();
                }
            }
        });

        // 启动时加载窗口布局配置与自定义积木
        OverlayManager.getInstance();
        ClientCommands.register();
        ClientCommands.loadCustomBlocks();
    }
}
