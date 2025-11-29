package io.github.westbot.mazefactory.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import io.github.westbot.mazefactory.Mazefactory;
import io.github.westbot.mazefactory.client.Maze;
import io.github.westbot.mazefactory.client.MazefactoryClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public class MazeRenderer {

    private static final int CHUNK_DIST = 2;

    // your tweakable settings
    public static float quadSize = 0.8f;
    public static float quadOpacity = 0.5f;

    // Fabric doc–style allocator + ring buffer
    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static MappableRingBuffer vertexRing;

    private static RenderPipeline pipelineDepthOn;
    private static RenderPipeline pipelineDepthOff;

    private static BufferBuilder builder;

    /** Must be called from ClientModInitializer */
    public static void init() {
        // Create pipelines using Fabric 1.21 style
        pipelineDepthOn = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Mazefactory.id("maze_depth_on"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .build();

        pipelineDepthOff = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Mazefactory.id("maze_depth_off"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();

        WorldRenderEvents.LAST.register(MazeRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        var maze = MazefactoryClient.maze;
        if (!MazefactoryClient.renderMaze || maze == null) return;

        extract(ctx, maze);
        draw(ctx);
    }

    // ---------------------- EXTRACTION STEP ----------------------------
    private static void extract(WorldRenderContext ctx, Maze maze) {
        var world = ctx.world();
        var cam = ctx.camera().getPosition();

        if (builder == null)
            builder = new BufferBuilder(ALLOCATOR, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        var camX = cam.x;
        var camY = cam.y;
        var camZ = cam.z;

        BlockPos camPos = BlockPos.containing(camX, 0, camZ);

        for (int dx = -CHUNK_DIST; dx <= CHUNK_DIST; dx++) {
            for (int dz = -CHUNK_DIST; dz <= CHUNK_DIST; dz++) {
                var chunk = world.getChunk(camPos.offset(dx * 16, 0, dz * 16));
                ChunkPos cpos = chunk.getPos();

                for (int cx = 0; cx < 16; cx++) {
                    for (int cz = 0; cz < 16; cz++) {

                        int x = SectionPos.sectionToBlockCoord(cpos.x, cx);
                        int z = SectionPos.sectionToBlockCoord(cpos.z, cz);

                        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        float fy = y + 0.05f;

                        var bp = new BlockPos(x, y, z);

                        if (!maze.isBlockInMap(bp))
                            continue;

                        var tile = maze.getTilePosition(bp);

                        int col = maze.getCellColor(tile.x, tile.y);

                        int a = (col >> 24) & 0xFF;
                        a = (int) (quadOpacity * a);
                        col = (col & 0x00FFFFFF) | (a << 24);

                        float half = quadSize * 0.5f;

                        float rx = x - (float) camX;
                        float ry = fy - (float) camY;
                        float rz = z - (float) camZ;

                        // two triangles
                        addQuad(rx + 0.5f, ry, rz + 0.5f, half, col);
                    }
                }
            }
        }
    }

    private static void addQuad(float x, float y, float z, float h, int col) {
        builder.addVertex(x - h, y, z - h).setColor(col);
        builder.addVertex(x - h, y, z + h).setColor(col);
        builder.addVertex(x + h, y, z + h).setColor(col);

        builder.addVertex(x - h, y, z - h).setColor(col);
        builder.addVertex(x + h, y, z + h).setColor(col);
        builder.addVertex(x + h, y, z - h).setColor(col);
    }

    // ---------------------- DRAW STEP ---------------------------------
    private static void draw(WorldRenderContext ctx) {
        if (builder == null) return;

        MeshData mesh = builder.build();
        if (mesh == null) return;

        var format = mesh.drawState().format();
        int size = mesh.vertexBuffer().remaining();

        if (vertexRing == null || vertexRing.size() < size) {
            vertexRing = new MappableRingBuffer(
                () -> "maze_renderer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                size
            );
        }

        GpuBufferSlice slice = vertexRing.currentBuffer().slice(0, size);

        try (GpuBuffer.MappedView mv =
                 RenderSystem.getDevice().createCommandEncoder().mapBuffer(slice, false, true)) {
            mv.data().put(mesh.vertexBuffer());
        }

        RenderPipeline pipeline =
            MazefactoryClient.mazeDepth ? pipelineDepthOn : pipelineDepthOff;

        // Issue the actual draw
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();

        try (RenderPass pass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                () -> "maze renderer draw",
                main.getColorTextureView(),
                OptionalInt.empty(),
                main.useDepth
                    ? main.getDepthTextureView()
                    : null,
                OptionalDouble.empty()
            )
        ) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);

            pass.setVertexBuffer(0, slice.buffer());

            // autogen indices
            RenderSystem.AutoStorageIndexBuffer sib =
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.TRIANGLES);
            pass.setIndexBuffer(sib.getBuffer(mesh.drawState().indexCount()), sib.type());

            pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
        }

        mesh.close();
        vertexRing.rotate();
        builder = null;
    }
}
