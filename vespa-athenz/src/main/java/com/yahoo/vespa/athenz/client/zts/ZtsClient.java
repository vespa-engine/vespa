// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import com.yahoo.security.Pkcs10Csr;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AwsRole;
import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import com.yahoo.vespa.athenz.api.ZToken;

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
    InstanceIdentity registerInstance(AthenzIdentity providerIdentity,
                                      AthenzIdentity instanceIdentity,
                                      String attestationData,
                                      Pkcs10Csr csr);

    /**
     * Refresh an existing instance
     *
     * @return A x509 certificate + service token (optional)
     */
    InstanceIdentity refreshInstance(AthenzIdentity providerIdentity,
                                     AthenzIdentity instanceIdentity,
                                     String instanceId,
                                     Pkcs10Csr csr);

    /**
     * Get service identity
     *
     * @return A x509 certificate with CA certificates
     */
    Identity getServiceIdentity(AthenzIdentity identity,
                                String keyId,
                                Pkcs10Csr csr);

    /**
     * Get service identity
     *
     * @return A x509 certificate with CA certificates
     */
    Identity getServiceIdentity(AthenzIdentity identity,
                                String keyId,
                                KeyPair keyPair,
                                String dnsSuffix);

    /**
     * Fetch a role token for the target domain
     *
     * @param domain Target domain
     * @return A role token
     */
    default ZToken getRoleToken(AthenzDomain domain) {
        return getRoleToken(domain, Duration.ofHours(1));
    }

    /**
     * Fetch a role token for the target domain
     *
     * @param domain Target domain
     * @param tokenExpiry Token expiry
     * @return A role token
     */
    ZToken getRoleToken(AthenzDomain domain, Duration tokenExpiry);

    /**
     * Fetch a role token for the target role
     *
     * @param athenzRole Target role
     * @return A role token
     */
    default ZToken getRoleToken(AthenzRole athenzRole) {
        return getRoleToken(athenzRole, Duration.ofHours(1));
    }

    /**
     * Fetch a role token for the target role
     *
     * @param athenzRole Target role
     * @param tokenExpiry Token expiry
     * @return A role token
     */
    ZToken getRoleToken(AthenzRole athenzRole, Duration tokenExpiry);

    /**
     * Fetch an access token for the target domain
     *
     * @param domain Target domain
     * @return An Athenz access token
     */
    default AthenzAccessToken getAccessToken(AthenzDomain domain) {
        return getAccessToken(domain, List.of());
    }

    /**
     * Fetch an access token for the target domain
     *
     * @param domain Target domain
     * @param proxyPrincipals List of principals to allow proxying token
     * @return An Athenz access token
     */
    AthenzAccessToken getAccessToken(AthenzDomain domain, List<AthenzIdentity> proxyPrincipals);

    /**
     * Fetch an access token for the target roles
     *
     * @param athenzRole List of athenz roles to get access token for
     * @return An Athenz access token
     */
    AthenzAccessToken getAccessToken(List<AthenzRole> athenzRole);

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

    /**
     * Get aws temporary credentials
     *
     * @param awsRole AWS role to get credentials for
     * @return AWS temporary credentials
     */
    default AwsTemporaryCredentials getAwsTemporaryCredentials(AthenzDomain athenzDomain, AwsRole awsRole) {
        return getAwsTemporaryCredentials(athenzDomain, awsRole, null, null);
    }

    /**
     * Get aws temporary credentials
     *
     * @param awsRole AWS role to get credentials for
     * @param externalId External Id to get credentials, or <code>null</code> if not required
     * @return AWS temporary credentials
     */
    default AwsTemporaryCredentials getAwsTemporaryCredentials(AthenzDomain athenzDomain, AwsRole awsRole, String externalId) {
        return getAwsTemporaryCredentials(athenzDomain, awsRole, null, externalId);
    }

    /**
     * Get aws temporary credentials
     *
     * @param awsRole AWS role to get credentials for
     * @param duration Duration for which the credentials should be valid, or <code>null</code> to use default
     * @param externalId External Id to get credentials, or <code>null</code> if not required
     * @return AWS temporary credentials
     */
    AwsTemporaryCredentials getAwsTemporaryCredentials(AthenzDomain athenzDomain, AwsRole awsRole, Duration duration, String externalId);

    /**
     * Check access to resource for a given principal
     *
     * @param resource The resource to verify access to
     * @param action Action to verify
     * @param identity Principal that requests access
     * @return <code>true</code> if access is allowed, <code>false</code> otherwise
     */
    boolean hasAccess(AthenzResourceName resource, String action, AthenzIdentity identity);

    void close();

}
