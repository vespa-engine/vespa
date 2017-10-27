// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;

import java.io.IOException;
import java.security.KeyPair;

/**
 * @author mortent
 */
public final class AthenzIdentityProviderImpl extends AbstractComponent implements AthenzIdentityProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private InstanceIdentity instanceIdentity;

    private final String dnsSuffix;
    private final String providerUniqueId;
    private final String domain;
    private final String service;

    @Inject
    public AthenzIdentityProviderImpl(IdentityConfig config) throws IOException {
        this(config, new ServiceProviderApi(config.loadBalancerAddress()), new AthenzService());
    }

    // Test only
    AthenzIdentityProviderImpl(IdentityConfig config,
                               ServiceProviderApi serviceProviderApi,
                               AthenzService athenzService) throws IOException {
        KeyPair keyPair = CryptoUtils.createKeyPair();
        this.domain = config.domain();
        this.service = config.service();
        String rawDocument = serviceProviderApi.getSignedIdentityDocument();
        SignedIdentityDocument document = objectMapper.readValue(rawDocument, SignedIdentityDocument.class);
        this.dnsSuffix = document.dnsSuffix;
        this.providerUniqueId = document.providerUniqueId;

        InstanceRegisterInformation instanceRegisterInformation = new InstanceRegisterInformation(
                document.providerService,
                this.domain,
                this.service,
                rawDocument,
                CryptoUtils.toPem(CryptoUtils.createCSR(domain, service, dnsSuffix, providerUniqueId, keyPair)),
                true
        );
        instanceIdentity = athenzService.sendInstanceRegisterRequest( instanceRegisterInformation, document.ztsEndpoint);
    }

    @Override
    public String getNToken() {
        return instanceIdentity.getServiceToken();
    }

    @Override
    public String getX509Cert() {
        return instanceIdentity.getX509Certificate();
    }

    @Override
    public String domain() {
        return domain;
    }

    @Override
    public String service() {
        return service;
    }
}

