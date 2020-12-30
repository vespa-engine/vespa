// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * This class implements the pair (code, message) that is used in Reply to hold errors.
 *
 * @author Simon Thoresen Hult
 */
public final class Error {

    private final int code;
    private final String message;
    private final String service;

    /**
     * This is the constructor used by anyone adding an error to a message. One does not manually need to set the
     * service name of an error, so ignore the other constructor when creating your own error instance.
     *
     * @param code The numerical code of this error.
     * @param message The description of this error.
     */
    public Error(int code, String message) {
        this.code = code;
        this.message = message;
        service = null;
    }

    /**
     * This constructor is used by the network layer to properly tag deserialized errors with the hostname of whatever
     * service produced the error. This constructor should NOT be used when manually creating errors.
     *
     * @param code The numerical code of this error.
     * @param message The description of this error.
     * @param service The service name of this error.
     */
    public Error(int code, String message, String service) {
        this.code = code;
        this.message = message;
        this.service = service;
    }

    /**
     * Return the numerical code of this error.
     *
     * @return The numerical code.
     */
    public int getCode() {
        return code;
    }

    /**
     * Return the description of this error.
     *
     * @return The description.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the name of the service on which this error occured.
     *
     * @return The service name.
     */
    public String getService() {
        return service;
    }

    /**
     * Returns whether or not this error is fatal, i.e. getCode() &gt;= ErrorCode.FATAL_ERROR.
     *
     * @return True, if this error is fatal.
     */
    public boolean isFatal() {
        return code >= ErrorCode.FATAL_ERROR;
    }

    @Override
    public String toString() {
        String name = ErrorCode.getName(code);
        return "[" +
               name + " @ " +
               (service != null ? service : "localhost") +
               "]: " + message;
    }
}
