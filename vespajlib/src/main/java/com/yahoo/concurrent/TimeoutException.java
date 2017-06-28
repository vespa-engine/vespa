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
