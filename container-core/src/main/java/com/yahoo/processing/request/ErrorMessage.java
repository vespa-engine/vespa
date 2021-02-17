// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request;

/**
 * An error encountered while processing a request.
 * This can be subclassed to add error messages containing more information.
 * <p>
 * Error messages are immutable.
 *
 * @author bratseth
 */
public class ErrorMessage implements Cloneable {

    private final int code;
    private final String message;
    private final String detailedMessage;
    private final Throwable cause;

    /**
     * Creates an error
     *
     * @param message the textual message describing this condition tersely
     */
    public ErrorMessage(String message) {
        this(0, message, null, null);
    }

    /**
     * Creates an error
     *
     * @param message the textual message describing this condition tersely
     * @param code an error code. If this is bound to HTTP request/responses and
     *             this error code is a HTTP status code, this code will be returned as the HTTP status
     */
    public ErrorMessage(int code, String message) {
        this(code, message, null, null);
    }

    /**
     * Creates an error
     *
     * @param message the textual message describing this condition tersely
     * @param details a longer detail description of this condition
     */
    public ErrorMessage(String message, String details) {
        this(0, message, details, null);
    }

    /**
     * Creates an error
     *
     * @param message the textual message describing this condition tersely
     * @param code an error code. If this is bound to HTTP request/responses and
     *             this error code is a HTTP status code, this code will be returned as the HTTP status
     * @param details a longer detail description of this condition
     */
    public ErrorMessage(int code, String message, String details) {
        this(code, message, details, null);
    }

    /**
     * Creates an error
     *
     * @param message the textual message describing this condition tersely
     * @param cause the cause of this error
     */
    public ErrorMessage(String message, Throwable cause) {
        this(0, message, null, cause);
    }

    /**
     * Creates an error
     *
     * @param code an error code. If this is bound to HTTP request/responses and
     *             this error code is a HTTP status code, this code will be returned as the HTTP status
     * @param message the textual message describing this condition tersely
     * @param cause the cause of this error
     */
    public ErrorMessage(int code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    /**
     * Creates an error
     *
     * @param message the textual message describing this condition tersely
     * @param details a longer detail description of this condition
     * @param cause the cause of this error
     */
    public ErrorMessage(String message, String details, Throwable cause) {
        this(0, message, details, cause);
    }

    /**
     * Creates an error
     *
     * @param code an error code. If this is bound to HTTP request/responses and
     *             this error code is a HTTP status code, this code will be returned as the HTTP status
     * @param message the textual message describing this condition tersely
     * @param details a longer detail description of this condition
     * @param cause the cause of this error
     */
    public ErrorMessage(int code, String message, String details, Throwable cause) {
        if (message == null) throw new NullPointerException("Message cannot be null");
        this.code = code;
        this.message = message;
        this.detailedMessage = details;
        this.cause = cause;
    }

    /**
     * Returns the code of this message, or 0 if no code is set
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the error message, never null
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns detailed information about this error, or null if there is no detailed message
     */
    public String getDetailedMessage() {
        return detailedMessage;
    }

    /**
     * Returns the throwable associated with this error, or null if none
     */
    public Throwable getCause() {
        return cause;
    }

    /**
     * Returns a formatted message containing the information in this
     */
    @Override
    public String toString() {
        if (code == 0 && detailedMessage == null && cause == null) return message; // shortcut
        StringBuilder b = new StringBuilder();
        if (code != 0)
            b.append(code).append(": ");
        b.append(message);
        if (detailedMessage != null)
            b.append(": ").append(detailedMessage);
        if (cause != null)
            append(cause, b);
        return b.toString();
    }

    private void append(Throwable t, StringBuilder b) {
        String lastMessage = null;
        String message;
        for (; t != null; t = t.getCause(), lastMessage = message) {
            message = getMessage(t);
            if (message == null) continue;
            if (lastMessage != null && lastMessage.equals(message)) continue;
            if (b.length() > 0)
                b.append(": ");
            b.append(message);
        }
    }

    /**
     * Returns a useful message from *this* exception, or null if none
     */
    private static String getMessage(Throwable t) {
        String message = t.getMessage();
        if (t.getCause() == null) {
            if (message == null) return t.getClass().getSimpleName();
        } else {
            if (message == null) return null;
            if (message.equals(t.getCause().getClass().getName() + ": " + t.getCause().getMessage())) return null;
        }
        return message;
    }

    @Override
    public int hashCode() {
        return code * 7 + message.hashCode() + (detailedMessage == null ? 0 : 17 * detailedMessage.hashCode());
    }

    /**
     * Two error messages are equal if they have the same code and message.
     * The cause is ignored in the comparison.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ErrorMessage)) return false;

        ErrorMessage other = (ErrorMessage) o;

        if (this.code != other.code) return false;

        if (!this.message.equals(other.message)) return false;

        if (this.detailedMessage == null) return other.detailedMessage == null;
        if (other.detailedMessage == null) return false;

        return this.detailedMessage.equals(other.detailedMessage);
    }

    @Override
    public ErrorMessage clone() {
        try {
            return (ErrorMessage) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error");
        }
    }

}
