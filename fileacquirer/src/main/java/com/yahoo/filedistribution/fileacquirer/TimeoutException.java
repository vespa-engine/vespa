// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.filedistribution.fileacquirer;

/**
 * @author Tony Vaagenes
 */
public class TimeoutException extends RuntimeException {

    /** Do not use this constructor */
    public TimeoutException() {
        super();
    }

    public TimeoutException(String message) {
        super(message);
    }

    public TimeoutException(String message,Throwable cause) {
        super(message,cause);
    }

}
