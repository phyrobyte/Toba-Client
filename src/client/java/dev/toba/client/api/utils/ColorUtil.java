/**
 * @author Fogma
 * @2026-02-4
 */


package dev.toba.client.api.utils;

public class ColorUtil {


    public static float[] intToFloatArray(int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new float[]{r, g, b, a};
    }


    public static int floatArrayToInt(float[] color) {
        int r = (int) (color[0] * 255) & 0xFF;
        int g = (int) (color[1] * 255) & 0xFF;
        int b = (int) (color[2] * 255) & 0xFF;
        int a = (int) (color[3] * 255) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }


    public static int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }


    public static int lighten(int color, int amount) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        int a = (color >> 24) & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }


    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }
}