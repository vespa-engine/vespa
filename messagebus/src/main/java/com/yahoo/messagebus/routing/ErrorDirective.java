// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * This class represents an error directive within a {@link Hop}'s selector. This means to stop whatever is being
 * resolved, and instead return a reply containing a specified error.
 *
 * @author Simon Thoresen Hult
 */
public class ErrorDirective implements HopDirective {

    private String msg;

    /**
     * Constructs a new error directive.
     *
     * @param msg The error message.
     */
    public ErrorDirective(String msg) {
        this.msg = msg;
    }

    /**
     * Returns the error string that is to be assigned to the reply.
     *
     * @return The error string.
     */
    public String getMessage() {
        return msg;
    }

    @Override
    public boolean matches(HopDirective dir) {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ErrorDirective)) {
            return false;
        }
        ErrorDirective rhs = (ErrorDirective)obj;
        if (!msg.equals(rhs.msg)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "(" + msg + ")";
    }

    @Override
    public String toDebugString() {
        return "ErrorDirective(msg = '" + msg + "')";
    }

    @Override
    public int hashCode() {
        return msg != null ? msg.hashCode() : 0;
    }
}
