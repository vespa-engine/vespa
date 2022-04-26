package com.yahoo.nativec;

import com.sun.jna.LastErrorException;

public class PosixFAdvise extends NativeC {
    public static final int POSIX_FADV_DONTNEED = 4; // See /usr/include/linux/fadvise.h
    private final static Throwable initException = loadLibrary();
    public static Throwable init() {
        return initException;
    }
    public static native int posix_fadvise(int fd, long offset, long len, int flag) throws LastErrorException;
}
