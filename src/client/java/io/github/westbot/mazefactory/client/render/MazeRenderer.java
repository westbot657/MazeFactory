package io.github.westbot.mazefactory.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
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
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class MazeRenderer {

    private static final int CHUNK_DIST = 2;
    public static float quadSize = 0.8f;
    public static float quadOpacity = 0.5f;

    public static RenderPipeline mazeDepthOn = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        .withLocation(Mazefactory.id("maze_depth_on"))
        .withDepthWrite(true)
        .withBlend(BlendFunction.ADDITIVE)
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
        .build();

    public static RenderPipeline mazeDepthOff = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withLocation(Mazefactory.id("maze_depth_off"))
        .withDepthWrite(false)
        .withBlend(BlendFunction.ADDITIVE)
        .withCull(false)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
        .build();

    private static final Minecraft minecraft = Minecraft.getInstance();
    public static void render(float dt, float x, float y, float z, GpuBufferSlice gpuBufferSlice) {
        var maze = MazefactoryClient.maze;
        if (maze == null) return;
        LevelAccessor levelAccessor = minecraft.level;
        if (levelAccessor == null) return;
        BlockPos blockPos = BlockPos.containing(x, 0, z);

        var tesselator = Tesselator.getInstance();
        var buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        var camPos = minecraft.gameRenderer.getMainCamera().getPosition().toVector3f();

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
                        var col = maze.getCellColor(lx, lz);

                        var alpha = (col >> 24) & 0xFF;
                        alpha = (int) (quadOpacity * (float) alpha);
                        col = (col & 0x00FFFFFF) | (alpha << 24);

                        var hd = quadSize / 2f;

                        var mx = ((float) lx) - camPos.x;
                        var mz = ((float) lz) - camPos.z;
                        var my = ly + camPos.y;

                        buffer.addVertex(mx - hd, my + 0.05f, mz - hd).setColor(col);
                        buffer.addVertex(mx - hd, my + 0.05f, mz + hd).setColor(col);
                        buffer.addVertex(mx + hd, my + 0.05f, mz + hd).setColor(col);

                        buffer.addVertex(mx - hd, my + 0.05f, mz - hd).setColor(col);
                        buffer.addVertex(mx + hd, my + 0.05f, mz + hd).setColor(col);
                        buffer.addVertex(mx + hd, my + 0.05f, mz - hd).setColor(col);

                    }
                }
            }
        }

        var buf = buffer.build();
        if (buf != null) {
            if (MazefactoryClient.mazeDepth) {
                drawWithShader(mazeDepthOn, gpuBufferSlice, buf);
            } else {
                drawWithShader(mazeDepthOff, gpuBufferSlice, buf);
            }
        }

    }

    public static void drawWithShader(RenderPipeline renderPipeline, GpuBufferSlice dynamicTransforms, MeshData meshData) {
        GpuBuffer vertexBuffer = renderPipeline.getVertexFormat().uploadImmediateVertexBuffer(meshData.vertexBuffer());
        GpuBuffer indexBuffer;
        VertexFormat.IndexType indexType;
        if (meshData.indexBuffer() == null) {
            RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(meshData.drawState().mode());
            indexBuffer = autoStorageIndexBuffer.getBuffer(meshData.drawState().indexCount());
            indexType = autoStorageIndexBuffer.type();
        } else {
            indexBuffer = renderPipeline.getVertexFormat().uploadImmediateIndexBuffer(meshData.indexBuffer());
            indexType = meshData.drawState().indexType();
        }

        RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
        GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : renderTarget.getColorTextureView();
        GpuTextureView depthTexture = renderTarget.useDepth ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : renderTarget.getDepthTextureView()) : null;
        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Immediate draw for " + renderPipeline.getLocation(), colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
            renderPass.setPipeline(renderPipeline);
            ScissorState scissorState = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissorState.enabled()) {
                renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
            }

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertexBuffer);
            for (int i = 0; i < 12; ++i) {
                GpuTextureView gpuTextureView3 = RenderSystem.getShaderTexture(i);
                if (gpuTextureView3 != null) {
                    renderPass.bindSampler("Sampler" + i, gpuTextureView3);
                }
            }

            renderPass.setIndexBuffer(indexBuffer, indexType);
            renderPass.drawIndexed(0, 0, meshData.drawState().indexCount(), 1);
        }

        meshData.close();
    }

}
