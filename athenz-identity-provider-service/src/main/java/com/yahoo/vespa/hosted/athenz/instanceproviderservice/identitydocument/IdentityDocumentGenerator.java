// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.identitydocument;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

/**
 * @author mortent
 */
public class IdentityDocumentGenerator {

    private final NodeRepository nodeRepository;
    private final Zone zone;
    private final KeyProvider keyProvider;
    private final AthenzProviderServiceConfig.Zones zoneConfig;

    @Inject
    public IdentityDocumentGenerator(AthenzProviderServiceConfig config,
                                     NodeRepository nodeRepository,
                                     Zone zone,
                                     KeyProvider keyProvider) {
        this.zoneConfig = Utils.getZoneConfig(config, zone);
        this.nodeRepository = nodeRepository;
        this.zone = zone;
        this.keyProvider = keyProvider;
    }

    public SignedIdentityDocument generateSignedIdentityDocument(String hostname) {
        Node node = nodeRepository.getNode(hostname).orElseThrow(() -> new RuntimeException("Unable to find node " + hostname));
        try {
            IdentityDocument identityDocument = generateIdDocument(node);
            String identityDocumentString = Utils.getMapper().writeValueAsString(identityDocument);

            String encodedIdentityDocument =
                    Base64.getEncoder().encodeToString(identityDocumentString.getBytes());
            Signature sigGenerator = Signature.getInstance("SHA512withRSA");

            PrivateKey privateKey = keyProvider.getPrivateKey(zoneConfig.secretVersion());
            sigGenerator.initSign(privateKey);
            sigGenerator.update(encodedIdentityDocument.getBytes());
            String signature = Base64.getEncoder().encodeToString(sigGenerator.sign());

            return new SignedIdentityDocument(
                    encodedIdentityDocument,
                    signature,
                    SignedIdentityDocument.DEFAULT_KEY_VERSION,
                    identityDocument.providerUniqueId.asString(),
                    toZoneDnsSuffix(zone, zoneConfig.certDnsSuffix()),
                    zoneConfig.domain() + "." + zoneConfig.serviceName(),
                    zoneConfig.ztsUrl(),
                    SignedIdentityDocument.DEFAULT_DOCUMENT_VERSION);
        } catch (Exception e) {
            throw new RuntimeException("Exception generating identity document: " + e.getMessage(), e);
        }
    }

    private IdentityDocument generateIdDocument(Node node) {
        Allocation allocation = node.allocation().orElseThrow(() -> new RuntimeException("No allocation for node " + node.hostname()));
        ProviderUniqueId providerUniqueId = new ProviderUniqueId(
                allocation.owner().tenant().value(),
                allocation.owner().application().value(),
                zone.environment().value(),
                zone.region().value(),
                allocation.owner().instance().value(),
                allocation.membership().cluster().id().value(),
                allocation.membership().index());

        return new IdentityDocument(
                providerUniqueId,
                "localhost", // TODO: Add configserver hostname
                node.hostname(),
                Instant.now());
    }

    private static String toZoneDnsSuffix(Zone zone, String dnsSuffix) {
        return zone.environment().value() + "-" + zone.region().value() + "." + dnsSuffix;
    }
}

