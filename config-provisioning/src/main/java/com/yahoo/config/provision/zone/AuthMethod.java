// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

import java.util.Optional;

/**
 * An endpoint's authentication method.
 *
 * @author mpolden
 */
public enum AuthMethod {

    /** Clients can authenticate with a certificate (mutual TLS) */
    mtls,

    /** Clients can authenticate with a secret token */
    token,

    /** Clients cannot authenticate with the endpoint directly */
    none;

    @Override
    public String toString() {
        return serialize();
    }

    public String serialize() {
        return switch(this) {
            case mtls -> "mtls";
            case token -> "token";
            case none -> "none";
        };
    }

    public static Optional<AuthMethod> fromString(String value) {
        if(value.equals(AuthMethod.mtls.serialize())) {
            return Optional.of(AuthMethod.mtls);
        }
        if(value.equals(AuthMethod.token.serialize())) {
            return Optional.of(AuthMethod.token);
        }
        if(value.equals(AuthMethod.none.serialize())) {
            return Optional.of(AuthMethod.none);
        }
        return Optional.empty();
    }

}
