package io.github.westbot.mazefactory.client.render;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;

public class MazeRenderer {

    private static final int CHUNK_DIST = 2;
    public static float quadSize = 0.8f;
    public static float quadOpacity = 0.5f;
    private final Minecraft minecraft = Minecraft.getInstance();

    public void renderHeightmap(PoseStack poseStack, float x, float y, float z) {
        LevelAccessor levelAccessor = this.minecraft.level;
        assert levelAccessor != null;
        BlockPos blockPos = BlockPos.containing(x, 0, z);

        var tesselator = Tesselator.getInstance();
        var buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int dx = -CHUNK_DIST; dx <= CHUNK_DIST; dx++) {
            for (int dz = -CHUNK_DIST; dz <= CHUNK_DIST; dz++) {
                ChunkAccess chunkAccess = levelAccessor.getChunk(blockPos.offset(dx * 16, 0, dz * 16));
                ChunkPos chunkPos = chunkAccess.getPos();

                for (int cx = 0; cx < 16; cx++) {
                    for (int cz = 0; cz < 16; cz++) {
                        int lx = SectionPos.sectionToBlockCoord(chunkPos.x, cx);
                        int lz = SectionPos.sectionToBlockCoord(chunkPos.z, cz);
                        float ly = levelAccessor.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                    }
                }

            }
        }


    }

}
