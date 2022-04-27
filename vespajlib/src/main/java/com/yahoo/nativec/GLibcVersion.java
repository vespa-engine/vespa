package com.yahoo.nativec;

/**
 * Gives access to the C library version.
 *
 * @author baldersheim
 */
public class GLibcVersion {
    private final static Throwable initException = NativeC.loadLibrary(GLibcVersion.class);
    public static Throwable init() {
        return initException;
    }
    private final String version;
    private final int major;
    private final int minor;
    public GLibcVersion() {
        version = gnu_get_libc_version();
        String [] parts = version.split("\\.");
        major = parts.length > 0 ? Integer.valueOf(parts[0]) : -1;
        minor = parts.length > 1 ? Integer.valueOf(parts[1]) : -1;
    }
    private native static String gnu_get_libc_version();
    public String version() { return version; }
    public int major() { return major; }
    public int minor() { return minor; }
}
