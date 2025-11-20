package io.github.westbot.mazefactory.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.westbot.mazefactory.Mazefactory;
import io.github.westbot.mazefactory.client.MazefactoryClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;

public class MazeRenderer {

    private static final int CHUNK_DIST = 2;
    public static float quadSize = 0.8f;
    public static float quadOpacity = 0.5f;

    public static RenderPipeline mazeDepthOn = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        .withDepthWrite(true)
        .withBlend(BlendFunction.ADDITIVE)
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
        .build();

    public static RenderPipeline mazeDepthOff = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.ADDITIVE)
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
        .build();

    private static final Minecraft minecraft = Minecraft.getInstance();
    public static void render(float dt, float x, float y, float z) {
        var maze = MazefactoryClient.maze;
        if (maze == null) return;
        LevelAccessor levelAccessor = minecraft.level;
        if (levelAccessor == null) return;
        BlockPos blockPos = BlockPos.containing(x, 0, z);
        for (int dx = -CHUNK_DIST; dx <= CHUNK_DIST; dx++) {
            for (int dz = -CHUNK_DIST; dz <= CHUNK_DIST; dz++) {
                ChunkAccess chunkAccess = levelAccessor.getChunk(blockPos.offset(dx * 16, 0, dz * 16));
                ChunkPos chunkPos = chunkAccess.getPos();
                for (int cx = 0; cx < 16; cx++) {
                    for (int cz = 0; cz < 16; cz++) {
                        int lx = SectionPos.sectionToBlockCoord(chunkPos.x, cx);
                        int lz = SectionPos.sectionToBlockCoord(chunkPos.z, cz);
                        float ly = levelAccessor.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz
                        ) + 0.05f;
                        BlockPos topPos = new BlockPos(lx, (int) ly, lz);
                        if (!maze.isBlockInMap(topPos)) continue;
                        // TODO: render quad
                    }
                }
            }
        }
    }
}
