package dev.toba.client.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {

    @Accessor("lastYaw")
    float getLastYaw();

    @Accessor("lastYaw")
    void setLastYaw(float value);

    @Accessor("lastPitch")
    float getLastPitch();

    @Accessor("lastPitch")
    void setLastPitch(float value);
}