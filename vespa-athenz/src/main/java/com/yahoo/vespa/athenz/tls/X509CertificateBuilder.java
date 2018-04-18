// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.DNS_NAME;

/**
 * @author bjorncs
 */
public class X509CertificateBuilder {

    private final long serialNumber;
    private final SignatureAlgorithm signingAlgorithm;
    private final PrivateKey caPrivateKey;
    private final Instant notBefore;
    private final Instant notAfter;
    private final List<SubjectAlternativeName> subjectAlternativeNames = new ArrayList<>();
    private final X500Principal issuer;
    private final X500Principal subject;
    private final PublicKey certPublicKey;
    private BasicConstraintsExtension basicConstraintsExtension;

    private X509CertificateBuilder(X500Principal issuer,
                                   X500Principal subject,
                                   Instant notBefore,
                                   Instant notAfter,
                                   PublicKey certPublicKey,
                                   PrivateKey caPrivateKey,
                                   SignatureAlgorithm signingAlgorithm,
                                   long serialNumber) {
        this.issuer = issuer;
        this.subject = subject;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.certPublicKey = certPublicKey;
        this.caPrivateKey = caPrivateKey;
        this.signingAlgorithm = signingAlgorithm;
        this.serialNumber = serialNumber;
    }

    public static X509CertificateBuilder fromCsr(Pkcs10Csr csr,
                                                 X500Principal caIssuer,
                                                 Instant notBefore,
                                                 Instant notAfter,
                                                 PrivateKey caPrivateKey,
                                                 SignatureAlgorithm signingAlgorithm,
                                                 long serialNumber) {
        try {
            PKCS10CertificationRequest bcCsr = csr.getBcCsr();
            PublicKey publicKey = new JcaPKCS10CertificationRequest(bcCsr).getPublicKey();
            return new X509CertificateBuilder(caIssuer,
                                              new X500Principal(bcCsr.getSubject().getEncoded()),
                                              notBefore,
                                              notAfter,
                                              publicKey,
                                              caPrivateKey,
                                              signingAlgorithm,
                                              serialNumber);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static X509CertificateBuilder fromKeypair(KeyPair keyPair,
                                                     X500Principal subject,
                                                     Instant notBefore,
                                                     Instant notAfter,
                                                     SignatureAlgorithm signingAlgorithm,
                                                     long serialNumber) {
        return new X509CertificateBuilder(subject,
                                          subject,
                                          notBefore,
                                          notAfter,
                                          keyPair.getPublic(),
                                          keyPair.getPrivate(),
                                          signingAlgorithm,
                                          serialNumber);
    }

    public X509CertificateBuilder addSubjectAlternativeName(String dnsName) {
        this.subjectAlternativeNames.add(new SubjectAlternativeName(DNS_NAME, dnsName));
        return this;
    }

    public X509CertificateBuilder addSubjectAlternativeName(SubjectAlternativeName san) {
        this.subjectAlternativeNames.add(san);
        return this;
    }

    public X509CertificateBuilder setBasicConstraints(boolean isCritical, boolean isCertAuthorityCertificate) {
        this.basicConstraintsExtension = new BasicConstraintsExtension(isCritical, isCertAuthorityCertificate);
        return this;
    }

    public X509Certificate build() {
        try {
            JcaX509v3CertificateBuilder jcaCertBuilder = new JcaX509v3CertificateBuilder(
                    issuer, BigInteger.valueOf(serialNumber), Date.from(notBefore), Date.from(notAfter), subject, certPublicKey);
            if (basicConstraintsExtension != null) {
                jcaCertBuilder.addExtension(
                        Extension.basicConstraints,
                        basicConstraintsExtension.isCritical,
                        new BasicConstraints(basicConstraintsExtension.isCertAuthorityCertificate));
            }
            if (!subjectAlternativeNames.isEmpty()) {
                GeneralNames generalNames = new GeneralNames(
                        subjectAlternativeNames.stream()
                                .map(SubjectAlternativeName::toGeneralName)
                                .toArray(GeneralName[]::new));
                jcaCertBuilder.addExtension(Extension.subjectAlternativeName, false, generalNames);
            }
            ContentSigner contentSigner = new JcaContentSignerBuilder(signingAlgorithm.getAlgorithmName())
                    .setProvider(BouncyCastleProviderHolder.getInstance())
                    .build(caPrivateKey);
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProviderHolder.getInstance())
                    .getCertificate(jcaCertBuilder.build(contentSigner));
        } catch (OperatorException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
