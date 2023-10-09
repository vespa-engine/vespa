// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.zone;

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

}
