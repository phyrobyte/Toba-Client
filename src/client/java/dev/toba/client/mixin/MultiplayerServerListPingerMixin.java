package dev.toba.client.mixin;

import dev.toba.client.api.proxy.ProxyManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.resolver.NoopAddressResolverGroup;
import net.minecraft.client.network.LegacyServerPinger;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.NetworkingBackend;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.util.List;

@Mixin(MultiplayerServerListPinger.class)
public abstract class MultiplayerServerListPingerMixin {
    @Inject(method = "ping", at = @At("HEAD"), cancellable = true)
    private void toba$legacyPingThroughProxy(InetSocketAddress address, ServerAddress serverAddress, ServerInfo serverInfo, NetworkingBackend networkingBackend, CallbackInfo ci) {
        ProxyManager proxyManager = ProxyManager.getInstance();
        if (!proxyManager.shouldProxy(address)) {
            return;
        }

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
                        pipeline.addLast(new LegacyServerPinger(serverAddress, (protocolVersion, versionText, labelText, onlinePlayers, maxPlayers) -> {
                            serverInfo.setStatus(ServerInfo.Status.INCOMPATIBLE);
                            serverInfo.version = Text.literal(versionText);
                            serverInfo.label = Text.literal(labelText);
                            serverInfo.playerCountLabel = MultiplayerServerListPinger.createPlayerCountText(onlinePlayers, maxPlayers);
                            serverInfo.players = new ServerMetadata.Players(maxPlayers, onlinePlayers, List.of());
                        }));
                    }
                });

        bootstrap.connect(address);
        ci.cancel();
    }
}
