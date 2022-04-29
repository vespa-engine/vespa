// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import com.yahoo.nativec.PosixFAdvise;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.SyncFailedException;
import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * Provides functionality only possible through native C library.
 */
public class NativeIO {
    private static final Logger logger = Logger.getLogger(NativeIO.class.getName());
    private static final String DISABLE_NATIVE_IO = "DISABLE_NATIVE_IO";
    private static final InitResult fdField = new InitResult();
    private static class InitResult {
        private final boolean initialized;
        private final boolean enabled;
        private final Field fdField;
        private final Throwable initError;
        InitResult() {
            boolean initComplete = false;
            boolean disabled = true;
            Field field = null;
            Throwable exception = null;
            try {
                exception = PosixFAdvise.init();
                if (exception == null) {
                    disabled = System.getenv().containsKey(DISABLE_NATIVE_IO);
                    if (!disabled) {
                        field = getField(FileDescriptor.class, "fd");
                        initComplete = true;
                    }
                }
            } catch (Throwable throwable) {
                exception = throwable;
            }
            initialized = initComplete;
            enabled = ! disabled;
            initError = exception;
            fdField = field;
        }
        private static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        }
        boolean isInitialized() { return initialized; }
        boolean isEnabled() { return enabled; }
        Throwable getError() { return initError; }
        int getNativeFD(FileDescriptor fd) {
            try {
                return fdField.getInt(fd);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public NativeIO() {
        if ( ! fdField.isInitialized()) {
            if (fdField.isEnabled()) {
                logger.warning("Native IO not possible due to " + getError().getMessage());
            } else {
                logger.info("Native IO has been disabled explicit via system property " + DISABLE_NATIVE_IO);
            }
        }
    }

    public boolean valid() { return fdField.isInitialized(); }
    public Throwable getError() { return fdField.getError(); }

    /**
     * Will hint the OS that data read so far will not be accessed again and should hence be dropped from the buffer cache.
     * @param fd The file descriptor to drop from buffer cache.
     */
    public void dropPartialFileFromCache(FileDescriptor fd, long offset, long len, boolean sync) {
        if (sync) {
            try {
                fd.sync();
            } catch (SyncFailedException e) {
                logger.warning("Sync failed while dropping cache: " + e.getMessage());
            }
        }
        if (valid()) {
            PosixFAdvise.posix_fadvise(fdField.getNativeFD(fd), offset, len, PosixFAdvise.POSIX_FADV_DONTNEED);
        }
    }
    /**
     * Will hint the OS that this is will not be accessed again and should hence be dropped from the buffer cache.
     * @param fd The file descriptor to drop from buffer cache.
     */
    public void dropFileFromCache(FileDescriptor fd) {
        dropPartialFileFromCache(fd, 0, 0, true);
    }

    /**
     * Will hint the OS that this is will not be accessed again and should hence be dropped from the buffer cache.
     * @param file File to drop from buffer cache
     */
    public void dropFileFromCache(File file) {
        try {
            dropFileFromCache(new FileInputStream(file).getFD());
        } catch (FileNotFoundException e) {
            logger.fine("No point in dropping a non-existing file from the buffer cache: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
