// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.common;

/**
 * We have tons of places where we throw exceptions when
 * some hosted resource is not found. This is usually
 * done with IllegalArgumentExceptions, java.ws.rs exceptions or
 * the servermodel runtime exceptions in the controller-server module.
 *
 * This is a checked alternative to do the same thing.
 *
 * @author smorgrav
 */
public class NotFoundCheckedException extends Exception {

    public NotFoundCheckedException() {
        super();
    }

    public NotFoundCheckedException(String msg) {
        super(msg);
    }
}
