package com.yahoo.io;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Logger;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Platform;

/**
 * Provides functionality only possible through native C library.
 */
public class NativeIO {
    private final static Logger logger = Logger.getLogger(NativeIO.class.getName());
    private static final int POSIX_FADV_DONTNEED = 4; // See /usr/include/linux/fadvise.h
    private static boolean initialized = false;
    private static Throwable initError = null;
    static {
        try {
            if (Platform.isLinux()) {
                Native.register(Platform.C_LIBRARY_NAME);
                initialized = true;
            }
        } catch (Throwable throwable) {
            initError = throwable;
        }
    }

    private static final Field fieldFD = getField(FileDescriptor.class, "fd");


    private static native int posix_fadvise(int fd, long offset, long len, int flag) throws LastErrorException;

    public NativeIO() {
        if (!initialized) {
            logger.warning("native IO not possible due to " + getError().getMessage());
        }
    }

    public boolean valid() { return initialized; }
    public Throwable getError() {
        if (initError != null) {
            return initError;
        } else {
            return new RuntimeException("Platform is unsúpported. Only supported on linux.");
        }
    }

    /**
     * Will hint the OS that this is will not be accessed again and should hence be dropped from the buffer cache.
     * @param fd The file descriptor to drop from buffer cache.
     */
    public void dropFileFromCache(FileDescriptor fd) {
        if (initialized) {
            posix_fadvise(getNativeFD(fd), 0, 0, POSIX_FADV_DONTNEED);
        }
    }

    /**
     * Will hint the OS that this is will not be accessed again and should hence be dropped from the buffer cache.
     * @param file File to drop from buffer cache
     */
    public void dropFileFromCache(File file) {
        try {
            dropFileFromCache(new FileInputStream(file).getFD());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static Field getField(Class<?> clazz, String fieldName) {
        Field field;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        field.setAccessible(true);
        return field;
    }

    private static int getNativeFD(FileDescriptor fd) {
        try {
            return fieldFD.getInt(fd);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
