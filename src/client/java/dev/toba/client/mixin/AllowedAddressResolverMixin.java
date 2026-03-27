package dev.toba.client.mixin;

import dev.toba.client.api.proxy.ProxyManager;
import dev.toba.client.api.proxy.UnresolvedProxyAddress;
import net.minecraft.client.network.Address;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.BlockListChecker;
import net.minecraft.client.network.RedirectResolver;
import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(AllowedAddressResolver.class)
public abstract class AllowedAddressResolverMixin {
    @Shadow @Final private RedirectResolver redirectResolver;
    @Shadow @Final private BlockListChecker blockListChecker;

    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private void toba$useUnresolvedTargetWhenProxyEnabled(ServerAddress serverAddress, CallbackInfoReturnable<Optional<Address>> cir) {
        ProxyManager proxyManager = ProxyManager.getInstance();
        if (!blockListChecker.isAllowed(serverAddress)) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        ServerAddress target = redirectResolver.lookupRedirect(serverAddress).orElse(serverAddress);
        if (!blockListChecker.isAllowed(target)) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        if (!proxyManager.shouldProxy(target)) {
            return;
        }

        cir.setReturnValue(Optional.of(new UnresolvedProxyAddress(target.getAddress(), target.getPort())));
    }
}
