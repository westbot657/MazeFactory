package io.github.westbot.mazefactory.client.mixin;

import io.github.westbot.mazefactory.client.MazefactoryClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(
        method = "handleChunkBlocksUpdate",
        at = @At("TAIL")
    )
    private void multiBlockUpdate(ClientboundSectionBlocksUpdatePacket clientboundSectionBlocksUpdatePacket, CallbackInfo ci) {
        if (MazefactoryClient.maze != null) {
            clientboundSectionBlocksUpdatePacket.runUpdates(MazefactoryClient.maze::updateBlock);
        }
    }

    @Inject(
        method = "handleBlockUpdate",
        at = @At("TAIL")
    )
    private void singleBlockUpdate(ClientboundBlockUpdatePacket clientboundBlockUpdatePacket, CallbackInfo ci) {
        if (MazefactoryClient.maze != null) {
            MazefactoryClient.maze.updateBlock(clientboundBlockUpdatePacket.getPos(), clientboundBlockUpdatePacket.getBlockState());
        }
    }

}
