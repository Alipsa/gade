package se.alipsa.gade.utils;

public class SystemUtils {

    public enum OS {
        WINDOWS, LINUX, MAC, UNSUPPORTED
    }

    public static OS getPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return OS.WINDOWS;
        }
        if (os.contains("mac")) {
            return OS.MAC;
        }
        if (os.contains("nux")) {
            return OS.LINUX;
        }
        return OS.UNSUPPORTED;

    }
}
