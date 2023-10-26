// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.protect;


/**
 * An error message with a code.
 * This class should be treated as immutable.
 *
 * @author bratseth
 */
public class ErrorMessage {

    /** An error code */
    protected int code;

    /** The short message of this error, always set */
    protected String message;

    /** The detailed instance message of this error, not always set */
    protected String detailedMessage = null;

    /** The cause of this error, or null if none is recorded */
    protected Throwable cause = null;

    /**
     * Create an invalid instance for a subclass to initialize.
     */
    public ErrorMessage() {
    }

    public ErrorMessage(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * Create an application specific error message with an application
     * specific code
     */
    public ErrorMessage(int code, String message, String detailedMessage) {
        this(code, message);
        this.detailedMessage = detailedMessage;
    }

    /** Create an application specific error message with an application specific code */
    public ErrorMessage(int code, String message, String detailedMessage, Throwable cause) {
        this(code, message, detailedMessage);
        this.cause = cause;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** Returns the detailed message, or null if there is no detailed message */
    public String getDetailedMessage() {
        return detailedMessage;
    }
   /**
     * Sets the cause of this. This should be set on errors which likely have their origin in plugin component code,
     * not on others.
     */
    public void setCause(Throwable cause) { this.cause=cause; }

    /** Returns the cause of this, or null if none is set */
    public Throwable getCause() { return cause; }

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

        if (this.detailedMessage==null) return other.detailedMessage==null;
        if (other.detailedMessage==null) return false;

        return this.detailedMessage.equals(other.detailedMessage);
    }

    @Override
    public String toString() {
        String details = "";

        if (detailedMessage != null) {
            details = detailedMessage;
        }
        if (cause !=null) {
            if (details.length()>0)
                details+=": ";
            details+= com.yahoo.yolean.Exceptions.toMessageString(cause);
        }
        if (details.length()>0)
            details=" (" + details + ")";

        return "error : " + message + details;
    }

}
