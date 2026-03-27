package dev.toba.client.api.proxy;

import net.minecraft.client.network.Address;

import java.net.InetSocketAddress;

public final class UnresolvedProxyAddress implements Address {
    private final String host;
    private final int port;
    private final InetSocketAddress socketAddress;

    public UnresolvedProxyAddress(String host, int port) {
        this.host = host;
        this.port = port;
        this.socketAddress = InetSocketAddress.createUnresolved(host, port);
    }

    @Override
    public String getHostName() {
        return host;
    }

    @Override
    public String getHostAddress() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public InetSocketAddress getInetSocketAddress() {
        return socketAddress;
    }
}
