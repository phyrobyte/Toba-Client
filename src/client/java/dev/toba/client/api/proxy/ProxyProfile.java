package dev.toba.client.api.proxy;

import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

public record ProxyProfile(
        UUID id,
        String name,
        ProxyType type,
        String host,
        int port,
        String username,
        String password
) {
    public ProxyProfile {
        id = id == null ? UUID.randomUUID() : id;
        name = normalize(name);
        type = type == null ? ProxyType.SOCKS5 : type;
        host = normalize(host);
        username = normalize(username);
        password = normalize(password);

        if (!type.supportsUsername()) {
            username = "";
        }
        if (!type.supportsPassword()) {
            password = "";
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public String displayName() {
        if (!name.isBlank()) {
            return name;
        }
        if (!host.isBlank() && port > 0) {
            return host + ":" + port;
        }
        return "Unnamed Proxy";
    }

    public boolean hasAuthentication() {
        return !username.isBlank() || !password.isBlank();
    }

    public SocketAddress proxyAddress() {
        return new InetSocketAddress(host, port);
    }

    public ProxyHandler createProxyHandler() {
        return switch (type) {
            case SOCKS5 -> hasAuthentication()
                    ? new Socks5ProxyHandler(proxyAddress(), username, password)
                    : new Socks5ProxyHandler(proxyAddress());
            case SOCKS4 -> username.isBlank()
                    ? new Socks4ProxyHandler(proxyAddress())
                    : new Socks4ProxyHandler(proxyAddress(), username);
        };
    }

    public boolean isPersistable() {
        return !host.isBlank() && port >= 1 && port <= 65535 && type != null;
    }

    public String authSummary() {
        if (type == ProxyType.SOCKS4) {
            return username.isBlank() ? "No user id" : "User ID set";
        }
        return hasAuthentication() ? "Credentials configured" : "No credentials";
    }

    public String shortSummary() {
        return type.getDisplayName() + " " + host + ":" + port;
    }
}
