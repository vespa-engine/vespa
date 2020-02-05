// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.secretstore;

/**
 * @author mortent
 */
public class SecretNotFoundException extends RuntimeException {

    public SecretNotFoundException(String message) {
        super(message);
    }
}
