package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.IdentityDocument;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.ProviderUniqueId;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.SignedIdentityDocument;
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

    public IdentityDocumentGenerator(NodeRepository nodeRepository, Zone zone, KeyProvider keyProvider) {
        this.nodeRepository = nodeRepository;
        this.zone = zone;
        this.keyProvider = keyProvider;
    }

    public String generateSignedIdentityDocument(String hostname) {
        Node node = nodeRepository.getNode(hostname).orElseThrow(() -> new RuntimeException("Unable to find node " + hostname));
        try {
            IdentityDocument identityDocument = generateIdDocument(node);
            String identityDocumentString = Utils.getMapper().writeValueAsString(identityDocument);

            String encodedIdentityDocument =
                    Base64.getEncoder().encodeToString(identityDocumentString.getBytes());
            Signature sigGenerator = Signature.getInstance("SHA512withRSA");

            // TODO: Get the correct version 0 ok for now
            PrivateKey privateKey = Crypto.loadPrivateKey(keyProvider.getPrivateKey(0));
            sigGenerator.initSign(privateKey);
            sigGenerator.update(encodedIdentityDocument.getBytes());
            String signature = Base64.getEncoder().encodeToString(sigGenerator.sign());

            SignedIdentityDocument signedIdentityDocument = new SignedIdentityDocument(
                    encodedIdentityDocument,
                    signature,
                    SignedIdentityDocument.DEFAULT_KEY_VERSION,
                    SignedIdentityDocument.DEFAILT_DOCUMENT_VERSION
            );
            return Utils.getMapper().writeValueAsString(signedIdentityDocument);
        } catch (Exception e) {
            throw new RuntimeException("Exception generating identity document: " + e.getMessage());
        }
    }

    private IdentityDocument generateIdDocument(Node node) {
        Allocation allocation = node.allocation().get();
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
                "localhost",
                node.hostname(),
                Instant.now());
    }

}

