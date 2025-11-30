package io.github.westbot.mazefactory.client;

import com.google.gson.JsonObject;
import io.github.westbot.mazefactory.Mazefactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector2i;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Maze {

    public enum CellState {
        EMPTY,
        PARTIAL,
        DONE,
    }

    public enum MazePart {
        WALL,
        FLOOR,
        SOLUTION_NODE,
    }

    public static final int BLACK = 0xFF000000;
    public static final int YELLOW = 0xFFFFF400;
    public static final int GREEN = 0xFF16FF00;

    public static final int WHITE = 0xFFFFFFFF;
    public static final int LIGHT_GRAY = 0xFF444444;
    public static final int BLUE = 0xFF22AA22;

    public static final int RED_1 = 0xFFFF0000;
    public static final int RED_2 = 0xFFBB2222;


    private String imagePath;
    private Vector2i offset;
    private int changes = 0;
    private BufferedImage image;
    private Vector2i size;

    public Maze(String imagePath, Vector2i offset) throws IOException {
        this.imagePath = imagePath;
        this.offset = offset;

        image = ImageIO.read(new File(imagePath));

        size = new Vector2i(image.getWidth(), image.getHeight());

    }

    public boolean isBlockInMap(BlockPos blockPos) {
        var x = blockPos.getX();
        var z = blockPos.getZ();

        return (offset.x <= x && x < offset.x + size.x) && (offset.y <= z && z < offset.y + size.y);
    }

    public Vector2i getTilePosition(BlockPos blockPos) {
        var x = blockPos.getX();
        var z = blockPos.getZ();

        return new Vector2i(x - offset.x, z - offset.y);
    }

    public void setCell(int x, int z, CellState state) {
        var color = image.getRGB(x, z);

        if (color == BLACK || color == YELLOW || color == GREEN) {
            if (state == CellState.PARTIAL) color = YELLOW;
            else if (state == CellState.DONE) color = GREEN;
            else color = BLACK;
        } else if (color == WHITE || color == LIGHT_GRAY ||  color == BLUE) {
            if (state == CellState.PARTIAL) color = LIGHT_GRAY;
            else if (state == CellState.DONE) color = BLUE;
            else color = WHITE;
        } else if (color == RED_1 || color == RED_2) {
            if (state == CellState.EMPTY) color = RED_1;
            else color = RED_2;
        }

        image.setRGB(x, z, color);
        // changes++;

        // if (changes > 25) save();

    }

    public MazePart getCellType(int x, int z) {
        int color = image.getRGB(x, z);

        // WALL colors
        if (color == BLACK || color == YELLOW || color == GREEN) {
            return MazePart.WALL;
        }

        // FLOOR colors
        if (color == WHITE || color == LIGHT_GRAY || color == BLUE) {
            return MazePart.FLOOR;
        }

        // SOLUTION_NODE colors
        if (color == RED_1 || color == RED_2) {
            return MazePart.SOLUTION_NODE;
        }

        // fallback if color is unknown
        return null;
    }


    public int getCellColor(int x, int z) {
        if (x < 0 || x >= size.x || z < 0 || z >= size.y) return 0xFF880000;
        if (image == null) return 0xFF880000;
        return image.getRGB(x, z);
    }

    public void save() {
        changes = 0;
        try {
            ImageIO.write(image, "png", new File(imagePath));
            var json = new JsonObject();
            json.addProperty("maze-path", imagePath);
            json.addProperty("offset-x", offset.x);
            json.addProperty("offset-z", offset.y);

            var s = json.toString();
            Files.writeString(new File(Minecraft.getInstance().gameDirectory.getAbsolutePath() + "/mazeconfig.json").toPath(), s);

        } catch (IOException e) {
            Mazefactory.LOGGER.error("Failed to save maze image", e);
        }
    }

    public boolean isMazeBlock(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    public void updateBlock(BlockPos pos, BlockState state) {
        if (isBlockInMap(pos)) {
            var tilePos = getTilePosition(pos);
            var tileType = getCellType(tilePos.x, tilePos.y);

            var level = Minecraft.getInstance().level;
            assert level != null;

            var top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
            var st  = level.getBlockState(top.below());
            var st1 = level.getBlockState(top.below().below());
            var st2 = level.getBlockState(top.below().below().below());

            CellState newState;
            if (tileType == MazePart.FLOOR || tileType == MazePart.SOLUTION_NODE) {
                if (isMazeBlock(st)) {
                    newState = CellState.DONE;
                } else {
                    newState = CellState.EMPTY;
                }
            } else {
                if (!isMazeBlock(st)) {
                    newState = CellState.EMPTY;
                } else if (!isMazeBlock(st1) || !isMazeBlock(st2)) {
                    newState = CellState.PARTIAL;
                } else {
                    newState = CellState.DONE;
                }
            }

            setCell(tilePos.x, tilePos.y, newState);

        }

    }

}
