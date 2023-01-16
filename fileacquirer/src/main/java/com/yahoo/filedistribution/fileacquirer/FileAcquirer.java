// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.filedistribution.fileacquirer;

import com.yahoo.config.FileReference;
import com.yahoo.vespa.config.FileReferenceDoesNotExistException;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Retrieves the path to a file or directory on the local file system
 * that has been transferred with the vespa file distribution
 * mechanism.
 *
 * @author Tony Vaagenes
 */
public interface FileAcquirer {

    /**
     * Returns the path to a file or directory corresponding to the
     * given file reference.  File references are produced by the
     * config system.
     *
     * @throws TimeoutException if the file or directory could not be retrieved in time.
     * @throws FileReferenceDoesNotExistException if the file is no
     *         longer available(due to reloading of config).
     */
    File waitFor(FileReference fileReference, long timeout, TimeUnit timeUnit) throws InterruptedException;

    void shutdown();

}
