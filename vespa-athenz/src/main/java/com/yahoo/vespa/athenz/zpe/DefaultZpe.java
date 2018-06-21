// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.zpe;

import com.yahoo.athenz.zpe.AuthZpeClient;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.ZToken;

import java.security.cert.X509Certificate;

/**
 * The default implementation of {@link Zpe}.
 * This implementation is currently based on the official Athenz ZPE library.
 *
 * @author bjorncs
 */
public class DefaultZpe implements Zpe {
    @Override
    public AuthorizationResult checkAccessAllowed(ZToken roleToken, AthenzResourceName resourceName, String action) {
        return AuthorizationResult.fromAccessCheckStatus(
                AuthZpeClient.allowAccess(roleToken.getRawToken(), resourceName.toResourceNameString(), action));
    }

    @Override
    public AuthorizationResult checkAccessAllowed(X509Certificate roleCertificate, AthenzResourceName resourceName, String action) {
        return AuthorizationResult.fromAccessCheckStatus(
                AuthZpeClient.allowAccess(roleCertificate, resourceName.toResourceNameString(), action));
    }

}
