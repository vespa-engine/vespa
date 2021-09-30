// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz;

/**
 * @author bjorncs
 */
public class AthenzIdentityProviderException extends RuntimeException {

    public AthenzIdentityProviderException(String message) {
        super(message);
    }

    public AthenzIdentityProviderException(String message, Throwable cause) {
        super(message, cause);
    }

}
