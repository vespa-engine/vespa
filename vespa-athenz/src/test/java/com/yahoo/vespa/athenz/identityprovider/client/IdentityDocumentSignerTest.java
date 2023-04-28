// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.ClusterType;
import com.yahoo.vespa.athenz.identityprovider.api.DefaultSignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.LegacySignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.yahoo.vespa.athenz.identityprovider.api.IdentityType.TENANT;
import static com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument.LEGACY_DEFAULT_DOCUMENT_VERSION;
import static com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument.DEFAULT_DOCUMENT_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class IdentityDocumentSignerTest {
    public static final int KEY_VERSION = 0;

    private static final IdentityType identityType = TENANT;
    private static final VespaUniqueInstanceId id =
            new VespaUniqueInstanceId(1, "cluster-id", "instance", "application", "tenant", "region", "environment", identityType);
    private static final AthenzService providerService = new AthenzService("vespa", "service");
    private static final KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
    private static final String configserverHostname = "configserverhostname";
    private static final String instanceHostname = "instancehostname";
    private static final Instant createdAt = Instant.EPOCH;
    private static final HashSet<String> ipAddresses = new HashSet<>(Arrays.asList("1.2.3.4", "::1"));
    private static final ClusterType clusterType = ClusterType.CONTAINER;
    private static final String ztsUrl = "https://foo";
    private static final AthenzIdentity serviceIdentity = new AthenzService("vespa", "node");

    @Test
    void legacy_generates_and_validates_signature() {
        IdentityDocumentSigner signer = new IdentityDocumentSigner();
        IdentityDocument identityDocument = new IdentityDocument(
                id, providerService, configserverHostname,
                instanceHostname, createdAt, ipAddresses, identityType, clusterType, ztsUrl, serviceIdentity);
        String signature =
                signer.generateLegacySignature(identityDocument, keyPair.getPrivate());

        SignedIdentityDocument signedIdentityDocument = new LegacySignedIdentityDocument(
                signature, KEY_VERSION, LEGACY_DEFAULT_DOCUMENT_VERSION, identityDocument);

        assertTrue(signer.hasValidSignature(signedIdentityDocument, keyPair.getPublic()));
    }

    @Test
    void generates_and_validates_signature() {
        IdentityDocumentSigner signer = new IdentityDocumentSigner();
        IdentityDocument identityDocument = new IdentityDocument(
                id, providerService, configserverHostname,
                instanceHostname, createdAt, ipAddresses, identityType, clusterType, ztsUrl, serviceIdentity);
        String data = EntityBindingsMapper.toIdentityDocmentData(identityDocument);
        String signature =
        signer.generateSignature(data, keyPair.getPrivate());

        SignedIdentityDocument signedIdentityDocument = new DefaultSignedIdentityDocument(
                signature, KEY_VERSION, DEFAULT_DOCUMENT_VERSION, data);

        assertTrue(signer.hasValidSignature(signedIdentityDocument, keyPair.getPublic()));
    }

    @Test
    void legacy_ignores_cluster_type_and_zts_url() {
        IdentityDocumentSigner signer = new IdentityDocumentSigner();
        IdentityDocument identityDocument = new IdentityDocument(
                id, providerService, configserverHostname,
                instanceHostname, createdAt, ipAddresses, identityType, clusterType, ztsUrl, serviceIdentity);
        IdentityDocument withoutIgnoredFields = new IdentityDocument(
                id, providerService, configserverHostname,
                instanceHostname, createdAt, ipAddresses, identityType, null, null, serviceIdentity);

        String signature =
                signer.generateLegacySignature(identityDocument, keyPair.getPrivate());

        var docWithoutIgnoredFields = new LegacySignedIdentityDocument(
                signature, KEY_VERSION, LEGACY_DEFAULT_DOCUMENT_VERSION, withoutIgnoredFields);
        var docWithIgnoredFields = new LegacySignedIdentityDocument(
                signature, KEY_VERSION, LEGACY_DEFAULT_DOCUMENT_VERSION, identityDocument);

        assertTrue(signer.hasValidSignature(docWithoutIgnoredFields, keyPair.getPublic()));
        assertEquals(docWithIgnoredFields.signature(), docWithoutIgnoredFields.signature());
    }

    @Test
    void validates_signature_for_new_and_old_versions() {
        IdentityDocumentSigner signer = new IdentityDocumentSigner();
        IdentityDocument identityDocument = new IdentityDocument(
                id, providerService, configserverHostname,
                instanceHostname, createdAt, ipAddresses, identityType, clusterType, ztsUrl, serviceIdentity);
        String signature =
                signer.generateLegacySignature(identityDocument, keyPair.getPrivate());

        SignedIdentityDocument signedIdentityDocument = new LegacySignedIdentityDocument(
                signature, KEY_VERSION, LEGACY_DEFAULT_DOCUMENT_VERSION, identityDocument);

        assertTrue(signer.hasValidSignature(signedIdentityDocument, keyPair.getPublic()));
    }
}