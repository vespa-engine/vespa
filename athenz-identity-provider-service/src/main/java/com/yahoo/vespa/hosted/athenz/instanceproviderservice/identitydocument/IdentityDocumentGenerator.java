// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.identitydocument;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.net.HostName;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.net.InetAddress;
import java.net.URI;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
            String identityDocumentString = Utils.getMapper().writeValueAsString(EntityBindingsMapper.toIdentityDocumentEntity(identityDocument));

            String encodedIdentityDocument =
                    Base64.getEncoder().encodeToString(identityDocumentString.getBytes());
            Signature sigGenerator = Signature.getInstance("SHA512withRSA");

            PrivateKey privateKey = keyProvider.getPrivateKey(zoneConfig.secretVersion());
            sigGenerator.initSign(privateKey);
            sigGenerator.update(encodedIdentityDocument.getBytes());
            String signature = Base64.getEncoder().encodeToString(sigGenerator.sign());

            return new SignedIdentityDocument(
                    identityDocument,
                    signature,
                    SignedIdentityDocument.DEFAULT_KEY_VERSION,
                    identityDocument.providerUniqueId(),
                    toZoneDnsSuffix(zone, zoneConfig.certDnsSuffix()),
                    new AthenzService(zoneConfig.domain(), zoneConfig.serviceName()),
                    URI.create(zoneConfig.ztsUrl()),
                    SignedIdentityDocument.DEFAULT_DOCUMENT_VERSION);
        } catch (Exception e) {
            throw new RuntimeException("Exception generating identity document: " + e.getMessage(), e);
        }
    }

    private IdentityDocument generateIdDocument(Node node) {
        Allocation allocation = node.allocation().orElseThrow(() -> new RuntimeException("No allocation for node " + node.hostname()));
        VespaUniqueInstanceId providerUniqueId = new VespaUniqueInstanceId(
                allocation.membership().index(),
                allocation.membership().cluster().id().value(),
                allocation.owner().instance().value(),
                allocation.owner().application().value(),
                allocation.owner().tenant().value(),
                zone.region().value(),
                zone.environment().value());

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

    /*
     * Basic access control until we have mutual auth where athenz x509certs are distributed on all docker nodes by node admin
     * Checks:
     *  If remote hostname == requested hostname --> OK
     *  If remote hostname is parent of requested hostname in node repo --> OK
     *  Otherwise NOT OK
     */
    // TODO Move this check to AuthorizationFilter in node-repository
    boolean validateAccess(String hostname, String remoteAddr) {
        try {
            InetAddress addr = InetAddress.getByName(remoteAddr);
            String remoteHostname = addr.getHostName();
            if (Objects.equals(hostname, remoteHostname)) {
                return true;
            }
            Node node = nodeRepository.getNode(hostname).orElseThrow(() -> new RuntimeException("Unable to find node " + hostname));
            return node.parentHostname()
                    .map(parent -> Objects.equals(parent, remoteHostname))
                    .orElse(false);
         } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

