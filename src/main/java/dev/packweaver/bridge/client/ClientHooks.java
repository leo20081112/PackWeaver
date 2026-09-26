package dev.packweaver.bridge.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端初始化钩子：由主入口在 CLIENT 环境下主动调用
 * （不再通过 fabric.mod.json 的 client 入口点，规避新版加载器
 * 对入口类的类加载差异导致的启动崩溃）。
 *
 * - F12：切换叠加层显示/隐藏
 * - Shift+F12：打开窗口布局编辑界面（拖拽移动、显示/隐藏各窗口）
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    public static void init() {
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

        // 启动时加载窗口布局配置与自定义积木；注册区域预览渲染器
        OverlayManager.getInstance();
        ClientCommands.register();
        ClientCommands.loadCustomBlocks();
        AreaPreview.isEnabled(); // 触发静态注册
    }
}
