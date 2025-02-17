package org.figuramc.figura.mixin.neoforge;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WingsLayer.class)
public interface ElytraLayerAccessor {
    @Invoker("getPlayerElytraTexture")
    static ResourceLocation getPlayerElytraTexture(HumanoidRenderState arg) {
        throw new AssertionError();
    }
}
