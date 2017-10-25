// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.InstanceConfirmation;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.ProviderUniqueId;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.SignedIdentityDocument;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Verifies that the instance's identity document is valid
 *
 * @author bjorncs
 */
public class InstanceValidator {

    private static final Logger log = Logger.getLogger(InstanceValidator.class.getName());

    private final KeyProvider keyProvider;

    public InstanceValidator(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public boolean isValidInstance(InstanceConfirmation instanceConfirmation) {
        SignedIdentityDocument signedIdentityDocument = instanceConfirmation.signedIdentityDocument;
        ProviderUniqueId providerUniqueId = signedIdentityDocument.identityDocument.providerUniqueId;
        log.log(LogLevel.INFO, () -> String.format("Validating instance %s.", providerUniqueId));
        PublicKey publicKey = keyProvider.getPublicKey(signedIdentityDocument.signingKeyVersion);
        if (isSignatureValid(publicKey, signedIdentityDocument.rawIdentityDocument, signedIdentityDocument.signature)) {
            log.log(LogLevel.INFO, () -> String.format("Instance %s is valid.", providerUniqueId));
            return true;
        }
        log.log(LogLevel.ERROR, () -> String.format("Instance %s has invalid signature.", providerUniqueId));
        return false;
    }

    public static boolean isSignatureValid(PublicKey publicKey, String rawIdentityDocument, String signature) {
        try {
            Signature signatureVerifier = Signature.getInstance("SHA512withRSA");
            signatureVerifier.initVerify(publicKey);
            signatureVerifier.update(rawIdentityDocument.getBytes());
            return signatureVerifier.verify(Base64.getDecoder().decode(signature));
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }
}
