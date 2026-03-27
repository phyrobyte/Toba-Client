package dev.toba.client.features.render.uptime;

/**
 * Interface for modules that display stats in the script overlay.
 */
public interface ScriptModule {
    long getStartTimeMillis();
    String[] getStatsLines();
}
