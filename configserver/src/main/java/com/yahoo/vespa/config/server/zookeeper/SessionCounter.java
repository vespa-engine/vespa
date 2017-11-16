// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.tenant.Tenants;
import com.yahoo.vespa.curator.Curator;

/**
 * A counter keeping track of session ids in an atomic fashion across multiple config servers.
 *
 * @author lulf
 * @since 5.1
 */
public class SessionCounter extends InitializedCounter {

    public SessionCounter(Curator curator, TenantName tenantName) {
        super(curator,
              Tenants.getTenantPath(tenantName).append("sessionCounter").getAbsolute(),
              Tenants.getSessionsPath(tenantName).getAbsolute());
    }

    /**
     * Atomically increment and return next session id.
     *
     * @return a new session id.
     */
    public long nextSessionId() {
        return counter.next();
    }
}
