package com.yahoo.nativec;

import com.sun.jna.LastErrorException;

/**
 * Gives access to the C library posix_fadvise() function.
 *
 * @author baldersheim
 */
public class PosixFAdvise {
    public static final int POSIX_FADV_DONTNEED = 4; // See /usr/include/linux/fadvise.h
    private final static Throwable initException = NativeC.loadLibrary(PosixFAdvise.class);
    public static Throwable init() {
        return initException;
    }
    public static native int posix_fadvise(int fd, long offset, long len, int flag) throws LastErrorException;
}
