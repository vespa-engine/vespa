package com.yahoo.concurrent;

/**
 * Throws on timeout
 * 
 * @author bratseth
 */
public class TimeoutException extends RuntimeException {
    
    private static final long serialVersionUID = 1245343;

    public TimeoutException(String message) {
        super(message);
    }
    
}
