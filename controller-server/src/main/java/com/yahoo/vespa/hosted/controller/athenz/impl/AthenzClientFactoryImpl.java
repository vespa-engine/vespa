// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.impl;

import com.google.inject.Inject;
import com.yahoo.athenz.auth.Principal;
import com.yahoo.athenz.auth.impl.PrincipalAuthority;
import com.yahoo.athenz.auth.impl.SimplePrincipal;
import com.yahoo.athenz.auth.token.PrincipalToken;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.zms.ZMSClient;
import com.yahoo.athenz.zts.ZTSClient;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsClient;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZtsClient;
import com.yahoo.vespa.hosted.controller.athenz.config.AthenzConfig;

import java.security.PrivateKey;

/**
 * @author bjorncs
 */
public class AthenzClientFactoryImpl implements AthenzClientFactory {

    private final SecretStore secretStore;
    private final AthenzConfig config;
    private final AthenzPrincipalAuthority athenzPrincipalAuthority;
    private final ServiceIdentityProvider identityProvider;

    @Inject
    public AthenzClientFactoryImpl(SecretStore secretStore, ServiceIdentityProvider identityProvider, AthenzConfig config) {
        this.secretStore = secretStore;
        this.identityProvider = identityProvider;
        this.config = config;
        this.athenzPrincipalAuthority = new AthenzPrincipalAuthority(config.principalHeaderName());
    }

    /**
     * @return A ZMS client instance with the service identity as principal.
     */
    @Override
    public ZmsClient createZmsClientWithServicePrincipal() {
        return new ZmsClientImpl(new ZMSClient(config.zmsUrl(), identityProvider.getIdentitySslContext()), config);
    }

    /**
     * @return A ZTS client instance with the service identity as principal.
     */
    @Override
    public ZtsClient createZtsClientWithServicePrincipal() {
        return new ZtsClientImpl(new ZTSClient(config.ztsUrl(), identityProvider.getIdentitySslContext()), config);
    }

    /**
     * @return A ZMS client created with a dual principal representing both the tenant admin and the service identity.
     */
    @Override
    public ZmsClient createZmsClientWithAuthorizedServiceToken(NToken authorizedServiceToken) {
        PrincipalToken signedToken = new PrincipalToken(authorizedServiceToken.getRawToken());
        AthenzConfig.Service service = config.service();
        signedToken.signForAuthorizedService(
                config.domain() + "." + service.name(), service.publicKeyId(), getServicePrivateKey());

        Principal dualPrincipal = SimplePrincipal.create(
                AthenzIdentities.USER_PRINCIPAL_DOMAIN.getName(), signedToken.getName(), signedToken.getSignedToken(), athenzPrincipalAuthority);
        return new ZmsClientImpl(new ZMSClient(config.legacyZmsUrl(), dualPrincipal), config);

    }

    private PrivateKey getServicePrivateKey() {
        AthenzConfig.Service service = config.service();
        String privateKey = secretStore.getSecret(service.privateKeySecretName(), service.privateKeyVersion()).trim();
        return Crypto.loadPrivateKey(privateKey);
    }

    private static class AthenzPrincipalAuthority extends PrincipalAuthority {
        private final String principalHeaderName;

        public AthenzPrincipalAuthority(String principalHeaderName) {
            this.principalHeaderName = principalHeaderName;
        }

        @Override
        public String getHeader() {
            return principalHeaderName;
        }
    }


}
