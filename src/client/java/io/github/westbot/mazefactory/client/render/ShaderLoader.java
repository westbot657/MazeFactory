package io.github.westbot.mazefactory.client.render;

import org.lwjgl.opengl.GL31;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ShaderLoader {

    private static String readResource(ResourceLocation rl) throws IOException {
        var rm = Minecraft.getInstance().getResourceManager();
        try (InputStream in = rm.open(rl)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int compileShader(String src, int type) {
        int shader = GL31.glCreateShader(type);
        GL31.glShaderSource(shader, src);
        GL31.glCompileShader(shader);

        int ok = GL31.glGetShaderi(shader, GL31.GL_COMPILE_STATUS);
        if (ok == 0) {
            String log = GL31.glGetShaderInfoLog(shader);
            throw new RuntimeException("Shader compilation failed: " + log);
        }
        return shader;
    }

    public static int loadProgram(ResourceLocation base) throws IOException {
        // base: mymod:shaders/myshader  → loads vsh + fsh
        ResourceLocation vsh = ResourceLocation.fromNamespaceAndPath(
            base.getNamespace(), base.getPath() + ".vsh"
        );
        ResourceLocation fsh = ResourceLocation.fromNamespaceAndPath(
            base.getNamespace(), base.getPath() + ".fsh"
        );

        String vsrc = readResource(vsh);
        String fsrc = readResource(fsh);

        int vs = compileShader(vsrc, GL31.GL_VERTEX_SHADER);
        int fs = compileShader(fsrc, GL31.GL_FRAGMENT_SHADER);

        int program = GL31.glCreateProgram();
        GL31.glAttachShader(program, vs);
        GL31.glAttachShader(program, fs);

        GL31.glLinkProgram(program);

        int ok = GL31.glGetProgrami(program, GL31.GL_LINK_STATUS);
        if (ok == 0) {
            String log = GL31.glGetProgramInfoLog(program);
            throw new RuntimeException("Shader link failed: " + log);
        }

        // shaders no longer needed after linking
        GL31.glDeleteShader(vs);
        GL31.glDeleteShader(fs);

        return program;
    }

    public static void use(int program) {
        GL31.glUseProgram(program);
    }

    public static void stop() {
        GL31.glUseProgram(0);
    }
}
