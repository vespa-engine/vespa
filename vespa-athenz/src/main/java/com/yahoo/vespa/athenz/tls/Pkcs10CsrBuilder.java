// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

/**
 * @author bjorncs
 */
public class Pkcs10CsrBuilder {

    private final X500Principal subject;
    private final KeyPair keyPair;
    private final List<String> subjectAlternativeNames = new ArrayList<>();
    private final SignatureAlgorithm signatureAlgorithm;

    private Pkcs10CsrBuilder(X500Principal subject,
                             KeyPair keyPair,
                             SignatureAlgorithm signatureAlgorithm) {
        this.subject = subject;
        this.keyPair = keyPair;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public static Pkcs10CsrBuilder fromKeypair(X500Principal subject,
                                               KeyPair keyPair,
                                               SignatureAlgorithm signatureAlgorithm) {
        return new Pkcs10CsrBuilder(subject, keyPair, signatureAlgorithm);
    }

    public Pkcs10CsrBuilder addSubjectAlternativeName(String san) {
        this.subjectAlternativeNames.add(san);
        return this;
    }

    public Pkcs10Csr build() {
        try {
            PKCS10CertificationRequestBuilder requestBuilder =
                    new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());

            if (!subjectAlternativeNames.isEmpty()) {
                GeneralNames generalNames = new GeneralNames(
                        subjectAlternativeNames.stream()
                                .map(san -> new GeneralName(GeneralName.dNSName, san))
                                .toArray(GeneralName[]::new));
                ExtensionsGenerator extGen = new ExtensionsGenerator();
                extGen.addExtension(Extension.subjectAlternativeName, false, generalNames);
                requestBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate());
            }
            ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm.getAlgorithmName())
                    .setProvider(BouncyCastleProviderHolder.getInstance())
                    .build(keyPair.getPrivate());
            return new Pkcs10Csr(requestBuilder.build(contentSigner));
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

}
