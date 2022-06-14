// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.OutputStream;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 * Should only be used internally in the log library
 */
interface LogTarget {
    /**
     * Opens an output stream for the target. If already open, the stream should be reopened.
     * @return a new outputstream for the log target.
     */
    public OutputStream open();

    /**
     * Close the log target, ensuring that all data is written.
     */
    public void close();
}
