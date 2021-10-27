// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.proxy.filedistribution;

import java.util.logging.Level;

import java.io.File;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Set last modification time for a file reference or downloaded url, to be able
 * to later clean up file references or urls not used for a long time.
 *
 * @author hmusum
 */
class RequestTracker {

    private final static Logger log = Logger.getLogger(RequestTracker.class.getName());

    void trackRequest(File file) {
        String absolutePath = file.getAbsolutePath();
        if ( ! file.exists())
            log.log(Level.WARNING, "Could not find file '" + absolutePath + "'");

        if ( ! file.setLastModified(Instant.now().toEpochMilli()))
            log.log(Level.WARNING, "Could not set last modified timestamp for '" + absolutePath + "'");
    }

}
