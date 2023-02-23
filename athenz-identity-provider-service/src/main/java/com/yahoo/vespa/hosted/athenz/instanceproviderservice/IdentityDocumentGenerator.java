// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.net.HostName;
import com.yahoo.security.KeyUtils;

import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.ClusterType;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.identityprovider.client.IdentityDocumentSigner;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;

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
    private final SecretStore secretStore;
    private final AthenzProviderServiceConfig athenzProviderServiceConfig;

    @Inject
    public IdentityDocumentGenerator(AthenzProviderServiceConfig config,
                                     NodeRepository nodeRepository,
                                     Zone zone,
                                     KeyProvider keyProvider,
                                     SecretStore secretStore) {
        this.athenzProviderServiceConfig = config;
        this.nodeRepository = nodeRepository;
        this.zone = zone;
        this.keyProvider = keyProvider;
        this.secretStore = secretStore;
    }

    public SignedIdentityDocument generateSignedIdentityDocument(String hostname, IdentityType identityType) {
        try {
            Node node = nodeRepository.nodes().node(hostname).orElseThrow(() -> new RuntimeException("Unable to find node " + hostname));
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

            Set<String> ips = new HashSet<>(node.ipConfig().primary());

            PrivateKey privateKey = privateKey(node);
            AthenzService providerService = new AthenzService(athenzProviderServiceConfig.domain(), athenzProviderServiceConfig.serviceName());

            String configServerHostname = HostName.getLocalhost();
            Instant createdAt = Instant.now();
            var clusterType = ClusterType.from(allocation.membership().cluster().type().name());
            String signature = signer.generateSignature(
                    providerUniqueId, providerService, configServerHostname,
                    node.hostname(), createdAt, ips, identityType, privateKey);
            return new SignedIdentityDocument(
                    signature, athenzProviderServiceConfig.secretVersion(), providerUniqueId, providerService,
                    SignedIdentityDocument.DEFAULT_DOCUMENT_VERSION, configServerHostname, node.hostname(),
                    createdAt, ips, identityType, clusterType, ztsUrl(node));
        } catch (Exception e) {
            throw new RuntimeException("Exception generating identity document: " + e.getMessage(), e);
        }
    }

    private PrivateKey privateKey(Node node) {
        // return sisSecret for public non-enclave hosts. secret otherwise
        if (zone.system().isPublic() && !node.cloudAccount().isEnclave(zone)) {
            String keyPem = secretStore.getSecret(athenzProviderServiceConfig.sisSecretName(), athenzProviderServiceConfig.sisSecretVersion());
            return KeyUtils.fromPemEncodedPrivateKey(keyPem);
        } else {
            return keyProvider.getPrivateKey(athenzProviderServiceConfig.secretVersion());
        }
    }
    private String ztsUrl(Node node) {
        // return sisUrl for public non-enclave hosts, ztsUrl otherwise
        if (zone.system().isPublic() && !node.cloudAccount().isEnclave(zone)) {
            return athenzProviderServiceConfig.sisUrl();
        } else {
            return athenzProviderServiceConfig.ztsUrl();
        }
    }
}

