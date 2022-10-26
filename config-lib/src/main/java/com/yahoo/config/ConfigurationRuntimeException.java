// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
 * This exception is thrown on internal errors in the configuration system.
 */
public class ConfigurationRuntimeException extends RuntimeException {

    public ConfigurationRuntimeException(String message) {
        super(message);
    }

    public ConfigurationRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationRuntimeException(Throwable cause) {
        super(cause);
    }

}
