// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.security.SignatureUtils;
import com.yahoo.vespa.athenz.identityprovider.api.V4SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

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

    public boolean hasValidSignature(SignedIdentityDocument doc, PublicKey publicKey) {
        try {
            Signature signer = SignatureUtils.createVerifier(publicKey);
            signer.initVerify(publicKey);
            signer.update(doc.data().getBytes(UTF_8));
            return signer.verify(Base64.getDecoder().decode(doc.signature()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
