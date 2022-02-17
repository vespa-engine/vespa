// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

/**
 * @author Ulf Lilleengen
 */
public class ConfigNotConvergedException extends RuntimeException {

    public ConfigNotConvergedException(Throwable t) {
        super(t);
    }

    public ConfigNotConvergedException(String message) {
        super(message);
    }

}
