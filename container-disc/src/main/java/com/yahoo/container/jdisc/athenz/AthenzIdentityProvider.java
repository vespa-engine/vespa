// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz;

import javax.net.ssl.SSLContext;

/**
 * @author mortent
 */
public interface AthenzIdentityProvider {
    String domain();
    String service();
    SSLContext getIdentitySslContext();
}
