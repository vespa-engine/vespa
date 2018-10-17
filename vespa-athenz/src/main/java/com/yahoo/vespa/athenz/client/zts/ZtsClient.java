// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.security.Pkcs10Csr;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

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
                                      String instanceId, // TODO Remove this parameter (unused/unnecessary)
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

    /**
     * Get service identity
     *
     * @return A x509 certificate with CA certificates
     */
    Identity getServiceIdentity(AthenzService identity,
                                String keyId,
                                Pkcs10Csr csr);

    /**
     * Get service identity
     *
     * @return A x509 certificate with CA certificates
     */
    Identity getServiceIdentity(AthenzService identity,
                                String keyId,
                                KeyPair keyPair,
                                String dnsSuffix);

    /**
     * Fetch a role token for the target domain
     *
     * @param domain Target domain
     * @return A role token
     */
    ZToken getRoleToken(AthenzDomain domain);

    /**
     * Fetch a role token for the target role
     *
     * @param athenzRole Target role
     * @return A role token
     */
    ZToken getRoleToken(AthenzRole athenzRole);

    /**
     * Fetch role certificate for the target domain and role
     *
     * @param role Target role
     * @param csr Certificate signing request matching role
     * @param expiry Certificate expiry
     * @return A role certificate
     */
    X509Certificate getRoleCertificate(AthenzRole role, Pkcs10Csr csr, Duration expiry);

    /**
     * Fetch role certificate for the target domain and role
     *
     * @param role Target role
     * @param csr Certificate signing request matching role
     * @return A role certificate
     */
    X509Certificate getRoleCertificate(AthenzRole role, Pkcs10Csr csr);

    /**
     * For a given provider, get a list of tenant domains that the user is a member of
     *
     * @param providerIdentity Provider identity
     * @param userIdentity User identity
     * @param roleName Role name
     * @return List of domains
     */
    List<AthenzDomain> getTenantDomains(AthenzIdentity providerIdentity, AthenzIdentity userIdentity, String roleName);

    void close();
}
