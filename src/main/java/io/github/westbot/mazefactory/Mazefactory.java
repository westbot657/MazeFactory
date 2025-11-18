package io.github.westbot.mazefactory;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mazefactory implements ModInitializer {

    public static final String MOD_ID = "mazefactory";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String name) {
        return ResourceLocation.tryBuild(MOD_ID, name);
    }

    @Override
    public void onInitialize() {
    }
}
