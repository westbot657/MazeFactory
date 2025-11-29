package io.github.westbot.mazefactory.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.westbot.mazefactory.Mazefactory;
import io.github.westbot.mazefactory.client.render.MazeRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector2i;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;


public class MazefactoryClient implements ClientModInitializer {

    public static final KeyMapping mazeRenderToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mazefactory.toggle_render", InputConstants.Type.KEYSYM, InputConstants.KEY_Z, "category.mazefactory.bindings"));

    public static final KeyMapping mazeDepthTestToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mazefactory.toggle_depth_test", InputConstants.Type.KEYSYM, InputConstants.KEY_X, "category.mazefactory.bindings"));

    public static Maze maze;

    public static boolean renderMaze = true;

    public static boolean mazeDepth = true;


    @Override
    public void onInitializeClient() {

        var configFile = new File(Minecraft.getInstance().gameDirectory.getAbsolutePath() + "/mazeconfig.json");

        MazeRenderer.init();

        ClientChunkEvents.CHUNK_LOAD.register(MazefactoryClient::updateChunk);


        if (configFile.exists() && configFile.isFile()) {
            try {
                var config = JsonParser.parseString(Files.readString(configFile.toPath())).getAsJsonObject();

                var path = config.get("maze-path").getAsString();
                var ox = config.get("offset-x").getAsInt();
                var oz = config.get("offset-z").getAsInt();

                maze = new Maze(path, new Vector2i(ox, oz));

            } catch (IOException e) {
                Mazefactory.LOGGER.error("Failed to load config");
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (mazeRenderToggle.consumeClick()) {
                renderMaze = !renderMaze;
                while (mazeRenderToggle.consumeClick());
            }

            if (mazeDepthTestToggle.consumeClick()) {
                mazeDepth = !mazeDepth;
                while (mazeDepthTestToggle.consumeClick());
            }

        });

        registerCommands();

    }

    private static void updateChunk(ClientLevel level, LevelChunk chunk) {

    }

    private int loadMaze(CommandContext<FabricClientCommandSource> context) {

        var path = StringArgumentType.getString(context, "path");
        var x = IntegerArgumentType.getInteger(context, "x");
        var z = IntegerArgumentType.getInteger(context, "z");

        try {
            maze = new Maze(path, new Vector2i(x, z));
        } catch (IOException e) {
            Mazefactory.LOGGER.error("Failed to load maze", e);
            context.getSource().sendError(Component.literal("Failed to load maze"));
            return -1;
        }
        return 1;
    }

    private int saveMaze(CommandContext<FabricClientCommandSource> context) {
        if (maze != null) maze.save();
        return 1;
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                literal("maze").then(
                    literal("set").then(
                        argument("path", StringArgumentType.string()).then(
                            argument("x", IntegerArgumentType.integer()).then(
                                argument("z", IntegerArgumentType.integer())
                                    .executes(this::loadMaze)
                            )
                        )
                    )
                ).then(
                    literal("save").executes(this::saveMaze)
                )
            );
        });
    }
}
