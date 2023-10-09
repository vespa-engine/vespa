// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

public class MalformedEventException extends Exception {
    public MalformedEventException (Throwable cause) {
        super(cause);
    }

    public MalformedEventException (String msg) {
        super(msg);
    }

    public MalformedEventException () {
    }
}
