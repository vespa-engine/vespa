// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.recipes;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class CuratorLockException extends RuntimeException {

    public CuratorLockException(String message, Exception e) {
        super(message, e);
    }

    public CuratorLockException(String message) {
        super(message);
    }

    public CuratorLockException(Exception e) {
        super(e);
    }
}
