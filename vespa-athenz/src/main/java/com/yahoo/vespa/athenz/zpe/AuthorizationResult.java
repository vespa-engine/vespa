// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.zpe;

import com.yahoo.athenz.zpe.AuthZpeClient.AccessCheckStatus;

import java.util.Arrays;

/**
 * The various types of access control results.
 *
 * @author bjorncs
 */
public enum AuthorizationResult {
    ALLOW(AccessCheckStatus.ALLOW),
    DENY(AccessCheckStatus.DENY),
    DENY_NO_MATCH(AccessCheckStatus.DENY_NO_MATCH),
    DENY_ROLETOKEN_EXPIRED(AccessCheckStatus.DENY_ROLETOKEN_EXPIRED),
    DENY_ROLETOKEN_INVALID(AccessCheckStatus.DENY_ROLETOKEN_INVALID),
    DENY_DOMAIN_MISMATCH(AccessCheckStatus.DENY_DOMAIN_MISMATCH),
    DENY_DOMAIN_NOT_FOUND(AccessCheckStatus.DENY_DOMAIN_NOT_FOUND),
    DENY_DOMAIN_EXPIRED(AccessCheckStatus.DENY_DOMAIN_EXPIRED),
    DENY_DOMAIN_EMPTY(AccessCheckStatus.DENY_DOMAIN_EMPTY),
    DENY_INVALID_PARAMETERS(AccessCheckStatus.DENY_INVALID_PARAMETERS),
    DENY_CERT_MISMATCH_ISSUER(AccessCheckStatus.DENY_CERT_MISMATCH_ISSUER),
    DENY_CERT_MISSING_SUBJECT(AccessCheckStatus.DENY_CERT_MISSING_SUBJECT),
    DENY_CERT_MISSING_DOMAIN(AccessCheckStatus.DENY_CERT_MISSING_DOMAIN),
    DENY_CERT_MISSING_ROLE_NAME(AccessCheckStatus.DENY_CERT_MISSING_ROLE_NAME);

    private final AccessCheckStatus wrappedElement;

    AuthorizationResult(AccessCheckStatus wrappedElement) {
        this.wrappedElement = wrappedElement;
    }

    public String getDescription() {
        return wrappedElement.toString();
    }

    static AuthorizationResult fromAccessCheckStatus(AccessCheckStatus accessCheckStatus) {
        return Arrays.stream(values())
                .filter(value -> value.wrappedElement == accessCheckStatus)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown status: " + accessCheckStatus));
    }

}
