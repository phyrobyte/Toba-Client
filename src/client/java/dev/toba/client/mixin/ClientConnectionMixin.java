package dev.toba.client.mixin;

import dev.toba.client.api.proxy.ProxyManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.network.handler.PacketSizeLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Shadow PacketSizeLogger packetSizeLogger;
    @Shadow public abstract void addFlowControlHandler(ChannelPipeline pipeline);

    @Inject(
            method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/network/NetworkingBackend;Lnet/minecraft/network/ClientConnection;)Lio/netty/channel/ChannelFuture;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void toba$connectThroughProxy(InetSocketAddress address, NetworkingBackend networkingBackend, ClientConnection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        ProxyManager proxyManager = ProxyManager.getInstance();
        if (!proxyManager.shouldProxy(address)) {
            return;
        }

        PacketSizeLogger packetSizeLogger = ((ClientConnectionMixin) (Object) connection).packetSizeLogger;
        Bootstrap bootstrap = new Bootstrap()
                .group(networkingBackend.getEventLoopGroup())
                .channel(networkingBackend.getChannelClass())
                .resolver(NoopAddressResolverGroup.INSTANCE)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ProxyManager.CONNECT_TIMEOUT_MILLIS)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        try {
                            channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                        } catch (ChannelException ignored) {
                        }

                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast("proxy", proxyManager.createActiveProxyHandler());
                        pipeline.addLast("timeout", new ReadTimeoutHandler(30));
                        ClientConnection.addHandlers(pipeline, NetworkSide.CLIENTBOUND, false, packetSizeLogger);
                        ((ClientConnectionMixin) (Object) connection).addFlowControlHandler(pipeline);
                    }
                });

        cir.setReturnValue(bootstrap.connect(address));
    }
}
