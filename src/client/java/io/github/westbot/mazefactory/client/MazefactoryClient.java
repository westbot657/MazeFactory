package io.github.westbot.mazefactory.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public class MazefactoryClient implements ClientModInitializer {

    public static final KeyMapping mazeRenderToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mazefactory.toggle_render", InputConstants.Type.KEYSYM, InputConstants.KEY_Z, "category.mazefactory.bindings"));

    public static final KeyMapping mazeDepthTestToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mazefactory.toggle_depth_test", InputConstants.Type.KEYSYM, InputConstants.KEY_X, "category.mazefactory.bindings"));


    public static boolean renderMaze = true;

    public static boolean mazeDepth = true;


    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (mazeRenderToggle.isDown()) {
                renderMaze = !renderMaze;
                while (mazeRenderToggle.isDown());
            }

            if (mazeDepthTestToggle.isDown()) {
                mazeDepth = !mazeDepth;
                while (mazeDepthTestToggle.isDown());
            }

        });

    }
}
