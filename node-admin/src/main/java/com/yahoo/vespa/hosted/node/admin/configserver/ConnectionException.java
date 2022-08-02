// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver;

import com.yahoo.vespa.hosted.node.admin.nodeadmin.ConvergenceException;
import org.apache.http.NoHttpResponseException;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * @author freva
 */
@SuppressWarnings("serial")
public class ConnectionException extends ConvergenceException {

    private ConnectionException(String message, Throwable cause) {
        super(message, cause, true);
    }

    /**
     * Returns {@link ConnectionException} if the given Throwable is of a known and well understood error or
     * a RuntimeException with the given exception as cause otherwise.
     */
    public static RuntimeException handleException(String prefix, Throwable t) {
        if (isKnownConnectionException(t))
            return new ConnectionException(prefix + t.getMessage(), t);

        return new RuntimeException(prefix, t);
    }

    static boolean isKnownConnectionException(Throwable t) {
        for (; t != null; t = t.getCause()) {
            if (t instanceof SocketException ||
                    t instanceof SocketTimeoutException ||
                    t instanceof NoHttpResponseException ||
                    t instanceof EOFException)
                return true;
        }

        return false;
    }
}
