// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.security.Pkcs10Csr;
import com.yahoo.vespa.athenz.api.AthenzAccessToken;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzResourceName;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.api.AwsRole;
import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import com.yahoo.vespa.athenz.api.ZToken;
import com.yahoo.vespa.athenz.client.zms.ZmsClient;
import com.yahoo.vespa.athenz.client.zts.Identity;
import com.yahoo.vespa.athenz.client.zts.InstanceIdentity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
public class ZtsClientMock implements ZtsClient {
    private static final Logger log = Logger.getLogger(ZtsClientMock.class.getName());

    private final AthenzDbMock athenz;
    private final Optional<ZmsClient> zmsClient;

    public ZtsClientMock(AthenzDbMock athenz) {
        this(athenz, null);
    }
    public ZtsClientMock(AthenzDbMock athenz, ZmsClient zmsClient) {
        this.athenz = athenz;
        this.zmsClient = Optional.ofNullable(zmsClient);
    }

    @Override
    public List<AthenzDomain> getTenantDomains(AthenzIdentity providerIdentity, AthenzIdentity userIdentity, String roleName) {
        log.log(Level.FINE, String.format("getTenantDomains(providerIdentity='%s', userIdentity='%s', roleName='%s')",
                                          providerIdentity.getFullName(), userIdentity.getFullName(), roleName));
        return athenz.domains.values().stream()
                .filter(domain -> domain.tenantAdmins.contains(userIdentity) || domain.admins.contains(userIdentity))
                .map(domain -> domain.name)
                .toList();
    }

    @Override
    public InstanceIdentity registerInstance(AthenzIdentity providerIdentity, AthenzIdentity instanceIdentity, String attestationData, Pkcs10Csr csr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InstanceIdentity refreshInstance(AthenzIdentity providerIdentity, AthenzIdentity instanceIdentity, String instanceId, Pkcs10Csr csr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Identity getServiceIdentity(AthenzIdentity identity, String keyId, Pkcs10Csr csr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Identity getServiceIdentity(AthenzIdentity identity, String keyId, KeyPair keyPair, String dnsSuffix) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ZToken getRoleToken(AthenzDomain domain, Duration expiry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ZToken getRoleToken(AthenzRole athenzRole, Duration expiry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AthenzAccessToken getAccessToken(AthenzDomain domain, List<AthenzIdentity> proxyPrincipals) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AthenzAccessToken getAccessToken(List<AthenzRole> athenzRole) {
        throw new UnsupportedOperationException();
    }

    @Override
    public X509Certificate getRoleCertificate(AthenzRole role, Pkcs10Csr csr, Duration expiry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public X509Certificate getRoleCertificate(AthenzRole role, Pkcs10Csr csr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AwsTemporaryCredentials getAwsTemporaryCredentials(AthenzDomain athenzDomain, AwsRole awsRole, Duration duration, String externalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasAccess(AthenzResourceName resource, String action, AthenzIdentity identity) {
        return zmsClient.orElseThrow(UnsupportedOperationException::new)
                .hasAccess(resource, action, identity);
    }

    @Override
    public void close() {

    }
}
