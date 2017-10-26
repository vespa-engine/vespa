// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz.impl;

import com.fasterxml.jackson.databind.JsonNode;
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
    AthenzIdentityProviderImpl(IdentityConfig config, ServiceProviderApi serviceProviderApi, AthenzService athenzService) throws IOException {
        KeyPair keyPair = CryptoUtils.createKeyPair();
        this.domain = config.domain();
        this.service = config.service();
        String signedIdentityDocument = serviceProviderApi.getSignedIdentityDocument();
        String ztsEndpoint = getZtsEndpoint(signedIdentityDocument);
        this.dnsSuffix = getDnsSuffix(signedIdentityDocument);
        this.providerUniqueId = getProviderUniqueId(signedIdentityDocument);
        String providerServiceName = getProviderServiceName(signedIdentityDocument);

        InstanceRegisterInformation instanceRegisterInformation = new InstanceRegisterInformation(
                providerServiceName,
                this.domain,
                this.service,
                signedIdentityDocument,
                CryptoUtils.toPem(CryptoUtils.createCSR(domain, service, dnsSuffix, providerUniqueId, keyPair)),
                true
        );
        instanceIdentity = athenzService.sendInstanceRegisterRequest(instanceRegisterInformation, ztsEndpoint);
    }

    private static String getProviderUniqueId(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "provider-unique-id");
    }

    private static String getDnsSuffix(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "dns-suffix");
    }

    private static String getProviderServiceName(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "provider-service");
    }

    private static String getZtsEndpoint(String signedIdentityDocument) throws IOException {
        return getJsonNode(signedIdentityDocument, "zts-endpoint");
    }

    private static String getJsonNode(String jsonString, String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonString);
        return jsonNode.get(path).asText();
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

