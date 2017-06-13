// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.recipes;

/**
 * @author lulf
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
