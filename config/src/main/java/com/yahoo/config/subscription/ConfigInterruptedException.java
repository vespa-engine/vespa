// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

/**
 * This exception is thrown when any blocking call within the Config API is interrupted.
 * @author Ulf Lilleengen
 * @deprecated  Will be removed in Vespa 8. Only for internal use.
 */
@SuppressWarnings("serial")
@Deprecated(forRemoval = true, since = "7")
public class ConfigInterruptedException extends RuntimeException {
    public ConfigInterruptedException(Throwable cause) {
        super(cause);
    }
}
