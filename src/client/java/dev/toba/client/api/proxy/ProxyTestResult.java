package dev.toba.client.api.proxy;

public record ProxyTestResult(boolean success, String message, String targetAddress, long durationMs) {
    public static ProxyTestResult success(String message, String targetAddress, long durationMs) {
        return new ProxyTestResult(true, message, targetAddress, durationMs);
    }

    public static ProxyTestResult failure(String message, String targetAddress, long durationMs) {
        return new ProxyTestResult(false, message, targetAddress, durationMs);
    }
}
