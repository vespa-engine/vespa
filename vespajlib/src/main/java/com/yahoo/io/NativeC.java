package com.yahoo.io;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Platform;

class NativeC {
    private final static Throwable initException = loadLibrary();
    private static Throwable loadLibrary() {
        if (Platform.isLinux()) {
            try {
                Native.register(Platform.C_LIBRARY_NAME);
            } catch (Throwable e) {
                return e;
            }
        } else {
            return new RuntimeException("Platform is unsúpported. Only supported on linux.");
        }
        return null;
    }
    static Throwable init() {
        return initException;
    }
    static native int posix_fadvise(int fd, long offset, long len, int flag) throws LastErrorException;
}
