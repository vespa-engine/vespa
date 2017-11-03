// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz;

import java.security.cert.X509Certificate;

/**
 * @author mortent
 */
public interface AthenzIdentityProvider {

    String getNToken();
    X509Certificate getX509Cert();
    String domain();
    String service();
}
