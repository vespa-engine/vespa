// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

/**
 * This exception is thrown when any blocking call within the Config API is interrupted.
 * @author Ulf Lilleengen
 */
@SuppressWarnings("serial")
public class ConfigInterruptedException extends RuntimeException {
    public ConfigInterruptedException(Throwable cause) {
        super(cause);
    }
}
