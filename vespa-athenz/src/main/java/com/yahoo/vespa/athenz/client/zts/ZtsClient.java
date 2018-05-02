// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;

import javax.net.ssl.SSLContext;

/**
 * Interface for a ZTS client.
 *
 * @author bjorncs
 */
public interface ZtsClient extends AutoCloseable {

    /**
     * Register an instance using the specified provider.
     *
     * @param attestationData The signed identity documented serialized to a string.
     * @return A x509 certificate + service token (optional)
     */
    InstanceIdentity registerInstance(AthenzService providerIdentity,
                                      AthenzService instanceIdentity,
                                      String instanceId,
                                      String attestationData,
                                      boolean requestServiceToken,
                                      Pkcs10Csr csr);

    /**
     * Refresh an existing instance
     *
     * @return A x509 certificate + service token (optional)
     */
    InstanceIdentity refreshInstance(AthenzService providerIdentity,
                                     AthenzService instanceIdentity,
                                     String instanceId,
                                     boolean requestServiceToken,
                                     Pkcs10Csr csr);
}
