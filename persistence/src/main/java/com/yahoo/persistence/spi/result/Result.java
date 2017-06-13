// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi.result;

/**
 * Represents a result from an SPI method invocation.
 */
public class Result {

    /**
     * Enumeration of the various categories of errors that can be returned
     * in a result.
     *
     * The categories are:
     *
     * TRANSIENT_ERROR: Operation failed, but may succeed if attempted again or on other data copies
     * PERMANENT_ERROR: Operation failed because it was somehow malformed or the operation parameters were wrong. Operation won't succeed
     * on other data copies either.
     * FATAL_ERROR: Operation failed in such a way that this node should be stopped (for instance, a disk failure). Operation will be retried
     * on other data copies.
     */
    public enum ErrorType {
        NONE,
        TRANSIENT_ERROR,
        PERMANENT_ERROR,
        UNUSED_ID,
        FATAL_ERROR
    }

    /**
     * Constructor to use for a result where there is no error.
     */
    public Result() {
    }

    /**
     * Creates a result with an error.
     *
     * @param type The type of error
     * @param message A human-readable error message to further detail the error.
     */
    public Result(ErrorType type, String message) {
        this.type = type;
        this.message = message;
    }

    public boolean equals(Result other) {
        return type.equals(other.type) &&
                message.equals(other.message);
    }

    @Override
    public boolean equals(Object otherResult) {
        if (otherResult instanceof Result) {
            return equals((Result)otherResult);
        }

        return false;
    }

    public boolean hasError() {
        return type != ErrorType.NONE;
    }

    public ErrorType getErrorType() {
        return type;
    }

    public String getErrorMessage() {
        return message;
    }

    @Override
    public String toString() {
        if (type == null) {
            return "Result(OK)";
        }

        return "Result(" + type.toString() + ", " + message + ")";
    }

    ErrorType type = ErrorType.NONE;
    String message = "";
}
