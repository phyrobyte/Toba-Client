package dev.toba.client.api.proxy;

public enum ProxyType {
    SOCKS5("SOCKS5", true, true),
    SOCKS4("SOCKS4", true, false);

    private final String displayName;
    private final boolean supportsUsername;
    private final boolean supportsPassword;

    ProxyType(String displayName, boolean supportsUsername, boolean supportsPassword) {
        this.displayName = displayName;
        this.supportsUsername = supportsUsername;
        this.supportsPassword = supportsPassword;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean supportsUsername() {
        return supportsUsername;
    }

    public boolean supportsPassword() {
        return supportsPassword;
    }

    public ProxyType next() {
        ProxyType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
