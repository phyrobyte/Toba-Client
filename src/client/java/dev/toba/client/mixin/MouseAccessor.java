package dev.toba.client.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mouse.class)
public interface MouseAccessor {

    @Accessor("cursorDeltaX")
    double getCursorDeltaX();

    @Accessor("cursorDeltaX")
    void setCursorDeltaX(double value);

    @Accessor("cursorDeltaY")
    double getCursorDeltaY();

    @Accessor("cursorDeltaY")
    void setCursorDeltaY(double value);
}
