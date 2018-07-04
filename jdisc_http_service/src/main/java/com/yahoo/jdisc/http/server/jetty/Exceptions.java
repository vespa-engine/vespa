// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

/**
 * Utility methods for exceptions
 *
 * @author Tony Vaagenes
 */
public class Exceptions {

    /**
     * Allows treating checked exceptions as unchecked.
     * Usage:
     * throw throwUnchecked(e);
     * The reason for the return type is to allow writing throw at the call site
     * instead of just calling throwUnchecked. Just calling throwUnchecked
     * means that the java compiler won't know that the statement will throw an exception,
     * and will therefore complain on things such e.g. missing return value.
     */
    public static RuntimeException throwUnchecked(Throwable e) {
        throwUncheckedImpl(e);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUncheckedImpl(Throwable t) throws T {
        throw (T)t;
    }

}
