package com.yahoo.nativec;

public class GLibcVersion {
    private final static Throwable initException = NativeC.loadLibrary(GLibcVersion.class);
    public static Throwable init() {
        return initException;
    }
    private final String version;
    public GLibcVersion() {
        version = gnu_get_libc_version();
    }
    private native static String gnu_get_libc_version();
    public String version() { return version; }
}
