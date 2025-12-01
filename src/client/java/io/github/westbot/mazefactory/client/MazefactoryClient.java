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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector2i;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;


public class MazefactoryClient implements ClientModInitializer {

    public static final KeyMapping mazeRenderToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mazefactory.toggle_render", InputConstants.Type.KEYSYM, InputConstants.KEY_Z, "category.mazefactory.bindings"));
    public static final KeyMapping mazeDualHeightToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mazefactory.toggle_double", InputConstants.Type.KEYSYM, InputConstants.KEY_RBRACKET, "category.mazefactory.bindings"));

    public static final KeyMapping mazeDepthTestToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mazefactory.toggle_depth_test", InputConstants.Type.KEYSYM, InputConstants.KEY_X, "category.mazefactory.bindings"));

    public static final KeyMapping mazeHeightLockToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mazefactory.toggle_height_lock", InputConstants.Type.KEYSYM, InputConstants.KEY_LBRACKET, "category.mazefactory.bindings"));

    public static Maze maze;

    public static boolean renderMaze = true;
    public static boolean renderPlayerY = true;
    public static boolean mazeDepth = true;
    public static boolean lockHeight = false;

    public static int targetHeight = 0;

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

            if (mazeDualHeightToggle.consumeClick()) {
                renderPlayerY = !renderPlayerY;
                while (mazeDualHeightToggle.consumeClick());
            }

            if (mazeHeightLockToggle.consumeClick()) {
                lockHeight = !lockHeight;
                var p = Minecraft.getInstance().player;
                if (p != null) {
                    targetHeight = (int) p.getPosition(0).y;
                }
                while (mazeHeightLockToggle.consumeClick());
            }

        });

        registerCommands();

    }

    private static void updateChunk(ClientLevel level, LevelChunk chunk) {
        if (maze != null) {

            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            int maxX = minX + 15;
            int maxZ = minZ + 15;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {

                    if (!maze.isBlockInMap(new BlockPos(x, 0, z))) continue;

                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
                    BlockState st = level.getBlockState(top);
                    maze.updateBlock(top, st);
                }
            }
        }
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
