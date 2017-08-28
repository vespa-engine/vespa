// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.concurrent;

/**
 * Throws on timeout
 * 
 * @author bratseth
 */
public class TimeoutException extends RuntimeException {
    
    public TimeoutException(String message) {
        super(message);
    }
    
}
