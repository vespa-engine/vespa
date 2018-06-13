// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.tls.SignatureAlgorithm;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Generates and validates the signature for a {@link SignedIdentityDocument}
 *
 * @author bjorncs
 */
public class IdentityDocumentSigner {

    public String generateSignature(VespaUniqueInstanceId providerUniqueId,
                                    AthenzService providerService,
                                    String configServerHostname,
                                    String instanceHostname,
                                    Instant createdAt,
                                    Set<String> ipAddresses,
                                    IdentityType identityType,
                                    PrivateKey privateKey) {
        try {
            Signature signer = createSigner();
            signer.initSign(privateKey);
            writeToSigner(signer, providerUniqueId, providerService, configServerHostname, instanceHostname, createdAt, ipAddresses, identityType);
            byte[] signature = signer.sign();
            return Base64.getEncoder().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasValidSignature(SignedIdentityDocument doc, PublicKey publicKey) {
        try {
            Signature signer = createSigner();
            signer.initVerify(publicKey);
            writeToSigner(signer, doc.providerUniqueId(), doc.providerService(), doc.configServerHostname(), doc.instanceHostname(), doc.createdAt(), doc.ipAddresses(), doc.identityType());
            return signer.verify(Base64.getDecoder().decode(doc.signature()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static Signature createSigner() throws NoSuchAlgorithmException {
        return Signature.getInstance(SignatureAlgorithm.SHA512_WITH_RSA.getAlgorithmName());
    }

    private static void writeToSigner(Signature signer,
                                      VespaUniqueInstanceId providerUniqueId,
                                      AthenzService providerService,
                                      String configServerHostname,
                                      String instanceHostname,
                                      Instant createdAt,
                                      Set<String> ipAddresses,
                                      IdentityType identityType) throws SignatureException {
        signer.update(providerUniqueId.asDottedString().getBytes(UTF_8));
        signer.update(providerService.getFullName().getBytes(UTF_8));
        signer.update(configServerHostname.getBytes(UTF_8));
        signer.update(instanceHostname.getBytes(UTF_8));
        ByteBuffer timestampAsBuffer = ByteBuffer.allocate(Long.BYTES);
        timestampAsBuffer.putLong(createdAt.toEpochMilli());
        signer.update(timestampAsBuffer.array());
        for (String ipAddress : new TreeSet<>(ipAddresses)) {
            signer.update(ipAddress.getBytes(UTF_8));
        }
        signer.update(identityType.id().getBytes(UTF_8));
    }
}
