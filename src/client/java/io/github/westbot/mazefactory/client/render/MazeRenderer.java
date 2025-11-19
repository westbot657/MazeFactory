package io.github.westbot.mazefactory.client.render;

import io.github.westbot.mazefactory.Mazefactory;
import io.github.westbot.mazefactory.client.MazefactoryClient;
import net.minecraft.client.Minecraft;
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

    private static int mazeShader = 0;

    private static int vao = 0;
    private static int vbo = 0;

    private static int attribPos = -1;
    private static int attribColor = -1;

    private static int uniformProj = -1;

    private static final int CHUNK_DIST = 2;
    public static float quadSize = 0.8f;
    public static float quadOpacity = 0.5f;

    private static final Minecraft minecraft = Minecraft.getInstance();

    private static final int FLOATS_PER_VERTEX = 7; // 3 pos + 4 color
    private static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;

    public static void render(float dt, float x, float y, float z) {

        if (!MazefactoryClient.renderMaze) return;
        // =============================================================================
        //  INITIALIZE SHADER + VAO ONE TIME
        // =============================================================================
        if (mazeShader == 0) {
            try {
                mazeShader = ShaderLoader.loadProgram(Mazefactory.id("shaders/maze"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Create VAO
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);

            // Create VBO
            vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            // Attribute locations
            attribPos = GL20.glGetAttribLocation(mazeShader, "inPosition");
            attribColor = GL20.glGetAttribLocation(mazeShader, "inColor");
            uniformProj = GL20.glGetUniformLocation(mazeShader, "uProjectionMatrix");

            if (attribPos == -1 || attribColor == -1 || uniformProj == -1) {
                throw new RuntimeException("Maze shader missing attributes or uniform");
            }

            // Position attribute
            GL20.glEnableVertexAttribArray(attribPos);
            GL20.glVertexAttribPointer(
                attribPos, 3, GL11.GL_FLOAT, false, STRIDE, 0
            );

            // Color attribute
            GL20.glEnableVertexAttribArray(attribColor);
            GL20.glVertexAttribPointer(
                attribColor, 4, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES
            );

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
        }

        // =============================================================================
        //  GET CAMERA POS
        // =============================================================================
        var cam = minecraft.gameRenderer.getMainCamera();
        float camX = (float) cam.getPosition().x;
        float camY = (float) cam.getPosition().y;
        float camZ = (float) cam.getPosition().z;

        // =============================================================================
        //  BUILD FLOATBUFFER PER FRAME
        // =============================================================================
        var maze = MazefactoryClient.maze;
        if (maze == null) return;

        LevelAccessor levelAccessor = minecraft.level;
        if (levelAccessor == null) return;

        BlockPos blockPos = BlockPos.containing(x, 0, z);

        int maxQuads = (2 * CHUNK_DIST + 1) * (2 * CHUNK_DIST + 1) * 16 * 16;
        FloatBuffer buffer = MemoryUtil.memAllocFloat(maxQuads * 6 * FLOATS_PER_VERTEX);

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

                        // Convert world → camera space
                        float rx = lx - camX;
                        float ry = ly - camY;
                        float rz = lz - camZ;

                        int col = maze.getCellColor(lx, lz);
                        float r = ((col >> 16) & 0xFF) / 255f;
                        float g = ((col >> 8) & 0xFF) / 255f;
                        float b = (col & 0xFF) / 255f;
                        float a = (((col >> 24) & 0xFF) / 255f) * quadOpacity;

                        float hs = quadSize / 2f;

                        // Write triangles using camera-relative coords
                        buffer.put(rx - hs).put(ry).put(rz - hs).put(r).put(g).put(b).put(a);
                        buffer.put(rx - hs).put(ry).put(rz + hs).put(r).put(g).put(b).put(a);
                        buffer.put(rx + hs).put(ry).put(rz + hs).put(r).put(g).put(b).put(a);

                        buffer.put(rx - hs).put(ry).put(rz - hs).put(r).put(g).put(b).put(a);
                        buffer.put(rx + hs).put(ry).put(rz + hs).put(r).put(g).put(b).put(a);
                        buffer.put(rx + hs).put(ry).put(rz - hs).put(r).put(g).put(b).put(a);
                    }
                }
            }
        }

        buffer.flip();
        int numVertices = buffer.limit() / FLOATS_PER_VERTEX;

        // =============================================================================
        //  SAVE OLD GL STATE
        // =============================================================================
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);

        int[] depthMaskArr = new int[1];
        GL11.glGetIntegerv(GL11.GL_DEPTH_WRITEMASK, depthMaskArr);
        boolean depthMask = depthMaskArr[0] != 0;

        int[] prevShaderArr = new int[1];
        GL11.glGetIntegerv(GL20.GL_CURRENT_PROGRAM, prevShaderArr);
        int prevShader = prevShaderArr[0];

        // =============================================================================
        //  APPLY OUR GL STATE
        // =============================================================================
        if (MazefactoryClient.mazeDepth) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
        } else {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
        }

        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // =============================================================================
        //  BIND SHADER + SEND PROJECTION MATRIX
        // =============================================================================
        GL20.glUseProgram(mazeShader);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            minecraft.gameRenderer.getProjectionMatrix(dt).get(fb);
            fb.flip();
            GL20.glUniformMatrix4fv(uniformProj, false, fb);
        }

        // =============================================================================
        //  DRAW
        // =============================================================================
        GL30.glBindVertexArray(vao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, numVertices);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(prevShader);

        // =============================================================================
        //  RESTORE STATE
        // =============================================================================
        if (depthTest) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(depthMask);

        if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);

        // =============================================================================
        //  FREE BUFFER
        // =============================================================================
        MemoryUtil.memFree(buffer);
    }
}
