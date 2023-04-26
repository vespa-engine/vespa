// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.security.SignatureUtils;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.DefaultSignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.LegacySignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;

import static com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument.LEGACY_DEFAULT_DOCUMENT_VERSION;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Generates and validates the signature for a {@link SignedIdentityDocument}
 *
 * @author bjorncs
 */
public class IdentityDocumentSigner {

    public String generateSignature(String identityDocumentData, PrivateKey privateKey) {
        try {
            Signature signer = SignatureUtils.createSigner(privateKey);
            signer.initSign(privateKey);
            signer.update(identityDocumentData.getBytes(UTF_8));
            byte[] signature = signer.sign();
            return Base64.getEncoder().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public String generateLegacySignature(IdentityDocument doc, PrivateKey privateKey) {
        return generateSignature(doc.providerUniqueId(), doc.providerService(), doc.configServerHostname(),
                                 doc.instanceHostname(), doc.createdAt(), doc.ipAddresses(), doc.identityType(), privateKey, doc.serviceIdentity());
    }

    // Cluster type is ignored due to old Vespa versions not forwarding unknown fields in signed identity document
    private String generateSignature(VespaUniqueInstanceId providerUniqueId,
                                    AthenzService providerService,
                                    String configServerHostname,
                                    String instanceHostname,
                                    Instant createdAt,
                                    Set<String> ipAddresses,
                                    IdentityType identityType,
                                    PrivateKey privateKey,
                                    AthenzIdentity serviceIdentity) {
        try {
            Signature signer = SignatureUtils.createSigner(privateKey);
            signer.initSign(privateKey);
            writeToSigner(
                    signer, providerUniqueId, providerService, configServerHostname, instanceHostname, createdAt,
                    ipAddresses, identityType);
            writeToSigner(signer, serviceIdentity);
            byte[] signature = signer.sign();
            return Base64.getEncoder().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasValidSignature(SignedIdentityDocument doc, PublicKey publicKey) {
        if (doc instanceof LegacySignedIdentityDocument signedDoc) {
            return validateLegacySignature(signedDoc, publicKey);
        } else if (doc instanceof DefaultSignedIdentityDocument signedDoc) {
            try {
                Signature signer = SignatureUtils.createVerifier(publicKey);
                signer.initVerify(publicKey);
                signer.update(signedDoc.data().getBytes(UTF_8));
                return signer.verify(Base64.getDecoder().decode(doc.signature()));
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("Unknown identity document type: " + doc.getClass().getName());
        }
    }

    private boolean validateLegacySignature(SignedIdentityDocument doc, PublicKey publicKey) {
        try {
            IdentityDocument iddoc = doc.identityDocument();
            Signature signer = SignatureUtils.createVerifier(publicKey);
            signer.initVerify(publicKey);
            writeToSigner(
                    signer, iddoc.providerUniqueId(), iddoc.providerService(), iddoc.configServerHostname(),
                    iddoc.instanceHostname(), iddoc.createdAt(), iddoc.ipAddresses(), iddoc.identityType());
            if (doc.documentVersion() >= LEGACY_DEFAULT_DOCUMENT_VERSION) {
                writeToSigner(signer, iddoc.serviceIdentity());
            }
            return signer.verify(Base64.getDecoder().decode(doc.signature()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
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

    private static void writeToSigner(Signature signer, AthenzIdentity serviceIdentity) throws SignatureException{
        signer.update(serviceIdentity.getFullName().getBytes(UTF_8));
    }
}
