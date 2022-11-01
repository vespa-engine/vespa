// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

/**
 * @author hakonhall
 */
public class ConfigServerException extends RuntimeException {
    public ConfigServerException(String message) { super(message); }
    public ConfigServerException(String message, Throwable cause) { super(message, cause); }
}
