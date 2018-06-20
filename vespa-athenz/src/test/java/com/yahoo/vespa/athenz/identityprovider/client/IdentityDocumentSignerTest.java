// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import org.junit.Test;

import java.net.URI;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;

import static com.yahoo.vespa.athenz.identityprovider.api.IdentityType.TENANT;
import static com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument.DEFAULT_DOCUMENT_VERSION;
import static com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument.DEFAULT_KEY_VERSION;
import static org.junit.Assert.*;

/**
 * @author bjorncs
 */
public class IdentityDocumentSignerTest {

    @Test
    public void generates_and_validates_signature() {
        IdentityDocumentSigner signer = new IdentityDocumentSigner();
        IdentityType identityType = TENANT;
        VespaUniqueInstanceId id =
                new VespaUniqueInstanceId(1, "cluster-id", "instance", "application", "tenant", "region", "environment", identityType);
        AthenzService providerService = new AthenzService("vespa", "service");
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        String configserverHostname = "configserverhostname";
        String instanceHostname = "instancehostname";
        Instant createdAt = Instant.EPOCH;
        HashSet<String> ipAddresses = new HashSet<>(Arrays.asList("1.2.3.4", "::1"));
        String signature =
                signer.generateSignature(id, providerService, configserverHostname, instanceHostname, createdAt, ipAddresses, identityType, keyPair.getPrivate());

        SignedIdentityDocument signedIdentityDocument = new SignedIdentityDocument(
                null, signature, DEFAULT_KEY_VERSION, id, "dns-suffix", providerService, URI.create("https://zts"),
                DEFAULT_DOCUMENT_VERSION, configserverHostname, instanceHostname, createdAt, ipAddresses, identityType);

        assertTrue(signer.hasValidSignature(signedIdentityDocument, keyPair.getPublic()));
    }

}