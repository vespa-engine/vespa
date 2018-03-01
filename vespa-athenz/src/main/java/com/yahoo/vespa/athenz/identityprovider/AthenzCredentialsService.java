// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.core.identity.IdentityConfig;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;

/**
 * @author bjorncs
 */
class AthenzCredentialsService {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final IdentityConfig identityConfig;
    private final IdentityDocumentService identityDocumentService;
    private final AthenzService athenzService;
    private final Clock clock;

    AthenzCredentialsService(IdentityConfig identityConfig,
                             IdentityDocumentService identityDocumentService,
                             AthenzService athenzService,
                             Clock clock) {
        this.identityConfig = identityConfig;
        this.identityDocumentService = identityDocumentService;
        this.athenzService = athenzService;
        this.clock = clock;
    }

    AthenzCredentials registerInstance() {
        KeyPair keyPair = CryptoUtils.createKeyPair();
        String rawDocument = identityDocumentService.getSignedIdentityDocument();
        SignedIdentityDocument document = parseSignedIdentityDocument(rawDocument);
        PKCS10CertificationRequest csr = CryptoUtils.createCSR(identityConfig.domain(),
                                                               identityConfig.service(),
                                                               document.dnsSuffix,
                                                               document.providerUniqueId,
                                                               keyPair);
        InstanceRegisterInformation instanceRegisterInformation =
                new InstanceRegisterInformation(document.providerService,
                                                identityConfig.domain(),
                                                identityConfig.service(),
                                                rawDocument,
                                                CryptoUtils.toPem(csr));
        InstanceIdentity instanceIdentity = athenzService.sendInstanceRegisterRequest(instanceRegisterInformation,
                                                                                      document.ztsEndpoint);
        return toAthenzCredentials(instanceIdentity, keyPair, document);
    }

    AthenzCredentials updateCredentials(AthenzCredentials currentCredentials) {
        SignedIdentityDocument document = currentCredentials.getIdentityDocument();
        KeyPair newKeyPair = CryptoUtils.createKeyPair();
        PKCS10CertificationRequest csr = CryptoUtils.createCSR(identityConfig.domain(),
                                                               identityConfig.service(),
                                                               document.dnsSuffix,
                                                               document.providerUniqueId,
                                                               newKeyPair);
        InstanceRefreshInformation refreshInfo = new InstanceRefreshInformation(CryptoUtils.toPem(csr));
        InstanceIdentity instanceIdentity =
                athenzService.sendInstanceRefreshRequest(document.providerService,
                                                         identityConfig.domain(),
                                                         identityConfig.service(),
                                                         document.providerUniqueId,
                                                         refreshInfo,
                                                         document.ztsEndpoint,
                                                         currentCredentials.getCertificate(),
                                                         currentCredentials.getKeyPair().getPrivate());
        return toAthenzCredentials(instanceIdentity, newKeyPair, document);
    }

    private AthenzCredentials toAthenzCredentials(InstanceIdentity instanceIdentity,
                                                         KeyPair keyPair,
                                                         SignedIdentityDocument identityDocument) {
        X509Certificate certificate = instanceIdentity.getX509Certificate();
        String serviceToken = instanceIdentity.getServiceToken();
        return new AthenzCredentials(serviceToken, certificate, keyPair, identityDocument);
    }

    private static SignedIdentityDocument parseSignedIdentityDocument(String rawDocument) {
        try {
            return mapper.readValue(rawDocument, SignedIdentityDocument.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
