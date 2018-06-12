// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.identitydocument;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.net.HostName;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.identityprovider.client.IdentityDocumentSigner;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.net.URI;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates a signed identity document for a given hostname and type
 *
 * @author mortent
 * @author bjorncs
 */
public class IdentityDocumentGenerator {

    private final IdentityDocumentSigner signer = new IdentityDocumentSigner();
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

    public SignedIdentityDocument generateSignedIdentityDocument(String hostname, IdentityType identityType) {
        Node node = nodeRepository.getNode(hostname).orElseThrow(() -> new RuntimeException("Unable to find node " + hostname));
        try {
            IdentityDocument identityDocument = generateIdDocument(node, identityType);

            PrivateKey privateKey = keyProvider.getPrivateKey(zoneConfig.secretVersion());
            AthenzService providerService = new AthenzService(zoneConfig.domain(), zoneConfig.serviceName());

            String signature = signer.generateSignature(
                    identityDocument.providerUniqueId(), providerService, identityDocument.configServerHostname(),
                    identityDocument.instanceHostname(), identityDocument.createdAt(), identityDocument.ipAddresses(), identityType, privateKey);

            return new SignedIdentityDocument(
                    identityDocument,
                    signature,
                    SignedIdentityDocument.DEFAULT_KEY_VERSION,
                    identityDocument.providerUniqueId(),
                    toZoneDnsSuffix(zone, zoneConfig.certDnsSuffix()),
                    providerService,
                    URI.create(zoneConfig.ztsUrl()),
                    SignedIdentityDocument.DEFAULT_DOCUMENT_VERSION,
                    identityDocument.configServerHostname(),
                    identityDocument.instanceHostname(),
                    identityDocument.createdAt(),
                    identityDocument.ipAddresses(),
                    identityType);
        } catch (Exception e) {
            throw new RuntimeException("Exception generating identity document: " + e.getMessage(), e);
        }
    }

    private IdentityDocument generateIdDocument(Node node, IdentityType identityType) {
        Allocation allocation = node.allocation().orElseThrow(() -> new RuntimeException("No allocation for node " + node.hostname()));
        VespaUniqueInstanceId providerUniqueId = new VespaUniqueInstanceId(
                allocation.membership().index(),
                allocation.membership().cluster().id().value(),
                allocation.owner().instance().value(),
                allocation.owner().application().value(),
                allocation.owner().tenant().value(),
                zone.region().value(),
                zone.environment().value(),
                identityType);

        Set<String> ips = new HashSet<>(node.ipAddresses());
        return new IdentityDocument(
                providerUniqueId,
                HostName.getLocalhost(),
                node.hostname(),
                Instant.now(),
                ips);
    }

    private static String toZoneDnsSuffix(Zone zone, String dnsSuffix) {
        return zone.environment().value() + "-" + zone.region().value() + "." + dnsSuffix;
    }

}

