// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Provides convenience methods to interact with Athenz authenticated services
 *
 * @author mortent
 * @author bjorncs
 */
public interface AthenzIdentityProvider {

    /**
     * Get the Athenz domain associated with this identity provider.
     *
     * @return The Athenz domain.
     */
    String domain();

    /**
     * Get the Athenz service name associated with this identity provider.
     *
     * @return The Athenz service name.
     */
    String service();

    /**
     * Get the SSLContext used for authenticating with the configured Athenz service
     *
     * @return An SSLContext for identity authentication.
     */
    SSLContext getIdentitySslContext();

    /**
     * Get the SSLContext for authenticating with an Athenz role
     *
     * @param domain Athenz domain name for the role
     * @param role Athenz role name
     * @return A SSLContext for role authentication within the specified domain and role.
     */
    SSLContext getRoleSslContext(String domain, String role);

    /**
     * Get a role token for the specified Athenz domain.
     *
     * @param domain The Athenz domain for the role token
     * @return A role token for the specified domain.
     */
    String getRoleToken(String domain);

    /**
     * Get a role token for a specific Athenz role.
     *
     * @param domain The Athenz domain name for the role
     * @param role The Athenz role name
     * @return A role token for the specified domain and role.
     */
    String getRoleToken(String domain, String role);

    /**
     * Get an access token for the specified Athenz domain.
     *
     * @param domain Athenz domain name for the token
     * @return An access token for the specified domain.
     */
    String getAccessToken(String domain);

    /**
     * Get an access token for a list of roles in an Athenz domain.
     *
     * @param domain Athenz domain name for the roles
     * @param roles The list of Athenz roles names
     * @return An access token for the specified roles.
     */
    String getAccessToken(String domain, List<String> roles);

    /**
     * Get an access token for the specified Athenz domain.
     *
     * @param domain Athenz domain name
     * @param roles List of Athenz role names. Empty list or null will fetch a token for all roles in the domain.
     * @param proxyPrincipal List of principals to allow proxying the token. Each principal must be provided as: <em>&lt;domain&gt;:service.&lt;service&gt;</em>
     *                       Empty list or <em>null</em> will return a token without proxy principals.
     * @return An access token for the specified domain.
     */
    String getAccessToken(String domain, List<String> roles, List<String> proxyPrincipal);

    /**
     * Get the X.509 identity certificate associated with this identity provider.
     *
     * @return The X.509 identity certificate.
     */
    List<X509Certificate> getIdentityCertificate();

    /**
     * Get the X.509 role certificate for a specific Athenz role.
     *
     * @param domain Athenz domain name for the role
     * @param role Athenz role name
     * @return An X.509 role certificate for the specified domain and role.
     */
    X509Certificate getRoleCertificate(String domain, String role);

    /**
     * Get the private key associated with this identity provider.
     *
     * @return The private key used for authentication.
     */
    PrivateKey getPrivateKey();

    /**
     * Get the path to the trust store used for SSL verification.
     *
     * @return The path to the trust store.
     */
    Path trustStorePath();

    void deconstruct();
}
