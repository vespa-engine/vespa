// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.filedistribution.fileacquirer;

import com.yahoo.config.FileReference;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * For use when testing searchers that uses file distribution.
 * @author Tony Vaagenes
 */
public abstract class MockFileAcquirer implements FileAcquirer {
    /** Creates a FileAcquirer that always returns the given file. **/
    public static FileAcquirer returnFile(final File file) {
        return new MockFileAcquirer() {
            @Override
            public File waitFor(FileReference fileReference,
                                long timeout, TimeUnit timeUnit) throws InterruptedException {
                return file;
            }
        };
    }

    /** Creates a FileAcquirer that maps from fileReference.value to a file. **/
    public static FileAcquirer returnFiles(final Map<String, File> files) {
        return new MockFileAcquirer() {
            @Override
            public File waitFor(FileReference fileReference,
                                long timeout, TimeUnit timeUnit) throws InterruptedException {
                return files.get(fileReference.value());
            }
        };
    }

    /** Creates a FileAcquirer that throws TimeoutException **/
    public static FileAcquirer throwTimeoutException() {
        return new MockFileAcquirer() {
            @Override
            public File waitFor(FileReference fileReference,
                                long timeout, TimeUnit timeUnit) throws InterruptedException {
                throw new TimeoutException("Timed out");
            }
        };
    }

    /** Creates a FileAcquirer that throws FileReferenceDoesNotExistException **/
    public static FileAcquirer throwFileReferenceDoesNotExistException() {
        return new MockFileAcquirer() {
            @Override
            public File waitFor(FileReference fileReference,
                                long timeout, TimeUnit timeUnit) throws InterruptedException {
                throw new FileReferenceDoesNotExistException(null);
            }
        };
    }

    @Override
    public void shutdown() {}
}
