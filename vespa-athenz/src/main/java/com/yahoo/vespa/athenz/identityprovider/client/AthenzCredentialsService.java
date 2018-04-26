// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocument;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrBuilder;
import com.yahoo.vespa.athenz.tls.Pkcs10CsrUtils;
import com.yahoo.vespa.athenz.tls.SignatureAlgorithm;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;
import com.yahoo.vespa.athenz.tls.SubjectAlternativeName;

import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Set;

import static com.yahoo.vespa.athenz.tls.KeyStoreType.JKS;
import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.IP_ADDRESS;

/**
 * @author bjorncs
 */
class AthenzCredentialsService {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final IdentityConfig identityConfig;
    private final IdentityDocumentService identityDocumentService;
    private final AthenzService athenzService;
    private final File trustStoreJks;

    AthenzCredentialsService(IdentityConfig identityConfig,
                             IdentityDocumentService identityDocumentService,
                             AthenzService athenzService,
                             File trustStoreJks) {
        this.identityConfig = identityConfig;
        this.identityDocumentService = identityDocumentService;
        this.athenzService = athenzService;
        this.trustStoreJks = trustStoreJks;
    }

    AthenzCredentials registerInstance() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        String rawDocument = identityDocumentService.getSignedIdentityDocument();
        SignedIdentityDocument document = parseSignedIdentityDocument(rawDocument);
        Pkcs10Csr csr = createCSR(identityConfig.domain(),
                                  identityConfig.service(),
                                  document.dnsSuffix,
                                  document.providerUniqueId,
                                  document.identityDocument.ipAddresses,
                                  keyPair);
        InstanceRegisterInformation instanceRegisterInformation =
                new InstanceRegisterInformation(document.providerService,
                                                identityConfig.domain(),
                                                identityConfig.service(),
                                                rawDocument,
                                                Pkcs10CsrUtils.toPem(csr));
        InstanceIdentity instanceIdentity = athenzService.sendInstanceRegisterRequest(instanceRegisterInformation,
                                                                                      document.ztsEndpoint);
        return toAthenzCredentials(instanceIdentity, keyPair, document);
    }

    AthenzCredentials updateCredentials(AthenzCredentials currentCredentials) {
        SignedIdentityDocument document = currentCredentials.getIdentityDocument();
        KeyPair newKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        Pkcs10Csr csr = createCSR(identityConfig.domain(),
                                  identityConfig.service(),
                                  document.dnsSuffix,
                                  document.providerUniqueId,
                                  document.identityDocument.ipAddresses,
                                  newKeyPair);
        InstanceRefreshInformation refreshInfo = new InstanceRefreshInformation(Pkcs10CsrUtils.toPem(csr));
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
        SSLContext identitySslContext = createIdentitySslContext(keyPair.getPrivate(), certificate);
        return new AthenzCredentials(serviceToken, certificate, keyPair, identityDocument, identitySslContext);
    }

    private SSLContext createIdentitySslContext(PrivateKey privateKey, X509Certificate certificate) {
        return new SslContextBuilder()
                .withKeyStore(privateKey, certificate)
                .withTrustStore(trustStoreJks, JKS)
                .build();
    }

    private static SignedIdentityDocument parseSignedIdentityDocument(String rawDocument) {
        try {
            return mapper.readValue(rawDocument, SignedIdentityDocument.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Pkcs10Csr createCSR(String identityDomain,
                                       String identityService,
                                       String dnsSuffix,
                                       String providerUniqueId,
                                       Set<String> ipAddresses,
                                       KeyPair keyPair) {
        X500Principal subject = new X500Principal(String.format("CN=%s.%s", identityDomain, identityService));
        // Add SAN dnsname <service>.<domain-with-dashes>.<provider-dnsname-suffix>
        // and SAN dnsname <provider-unique-instance-id>.instanceid.athenz.<provider-dnsname-suffix>
        Pkcs10CsrBuilder pkcs10CsrBuilder = Pkcs10CsrBuilder.fromKeypair(subject, keyPair, SignatureAlgorithm.SHA256_WITH_RSA)
                .addSubjectAlternativeName(String.format("%s.%s.%s",
                                                         identityService,
                                                         identityDomain.replace(".", "-"),
                                                         dnsSuffix))
                .addSubjectAlternativeName(String.format("%s.instanceid.athenz.%s",
                                                         providerUniqueId,
                                                         dnsSuffix));
        if(ipAddresses != null) {
            ipAddresses.forEach(ipaddress ->  pkcs10CsrBuilder.addSubjectAlternativeName(new SubjectAlternativeName(IP_ADDRESS, ipaddress)));
        }
        return pkcs10CsrBuilder.build();
    }
}
