// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.exception;

/**
 * This exception wraps any exception thrown by docker-java
 */
@SuppressWarnings("serial")
public class DockerException extends RuntimeException {
    public DockerException(String message) {
        super(message);
    }

    public DockerException(String message, Exception exception) {
        super(message, exception);
    }
}
