// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.zpe;

import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.ZToken;

import java.security.cert.X509Certificate;

/**
 * Interface for interacting with ZPE (Authorization Policy Engine)
 *
 * @author bjorncs
 */
public interface Zpe {
    AuthorizationResult checkAccessAllowed(ZToken roleToken, AthenzResourceName resourceName, String action);
    AuthorizationResult checkAccessAllowed(X509Certificate roleCertificate, AthenzResourceName resourceName, String action);
}
