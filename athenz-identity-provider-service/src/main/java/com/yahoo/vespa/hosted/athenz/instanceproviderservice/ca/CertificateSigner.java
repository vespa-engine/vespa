// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.Utils.getZoneConfig;


/**
 * Signs Certificate Signing Reqest from tenant nodes. This certificate will be used
 * by nodes to authenticate themselves when performing operations against the config
 * server, such as updating node-repository or orchestrator.
 *
 * @author freva
 */
public class CertificateSigner {

    private static final Logger log = Logger.getLogger(CertificateSigner.class.getName());

    static final String SIGNER_ALGORITHM = "SHA256withRSA";
    static final Duration CERTIFICATE_EXPIRATION = Duration.ofDays(30);
    private static final List<ASN1ObjectIdentifier> ILLEGAL_EXTENSIONS = ImmutableList.of(
            Extension.basicConstraints, Extension.subjectAlternativeName);

    private final JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter();
    private final Provider provider = new BouncyCastleProvider();

    private final PrivateKey caPrivateKey;
    private final X500Name issuer;
    private final Clock clock;

    @Inject
    public CertificateSigner(KeyProvider keyProvider,
                             ConfigserverConfig configserverConfig,
                             AthenzProviderServiceConfig config,
                             Zone zone) {
        this(getPrivateKey(keyProvider, config, zone), configserverConfig.loadBalancerAddress(), Clock.systemUTC());
    }

    CertificateSigner(PrivateKey caPrivateKey, String loadBalancerAddress, Clock clock) {
        this.caPrivateKey = caPrivateKey;
        this.issuer = new X500Name("CN=" + loadBalancerAddress);
        this.clock = clock;
    }

    /**
     * Signs the CSR if:
     * <ul>
     *  <li>Common Name matches {@code remoteHostname}</li>
     *  <li>CSR does not contain any any of the extensions in {@code ILLEGAL_EXTENSIONS}</li>
     * </ul>
     */
    X509Certificate generateX509Certificate(PKCS10CertificationRequest certReq, String remoteHostname) {
        verifyCertificateCommonName(certReq.getSubject(), remoteHostname);
        verifyCertificateExtensions(certReq);

        Date notBefore = Date.from(clock.instant());
        Date notAfter = Date.from(clock.instant().plus(CERTIFICATE_EXPIRATION));

        try {
            PublicKey publicKey = new JcaPKCS10CertificationRequest(certReq).getPublicKey();
            X509v3CertificateBuilder caBuilder = new JcaX509v3CertificateBuilder(
                    issuer, BigInteger.valueOf(clock.millis()), notBefore, notAfter, certReq.getSubject(), publicKey)

                    // Set Basic Constraints to false
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

            ContentSigner caSigner = new JcaContentSignerBuilder(SIGNER_ALGORITHM)
                    .setProvider(provider)
                    .build(caPrivateKey);

            return certificateConverter
                    .setProvider(provider)
                    .getCertificate(caBuilder.build(caSigner));
        } catch (Exception ex) {
            log.log(LogLevel.ERROR, "Failed to generate X509 Certificate", ex);
            throw new RuntimeException("Failed to generate X509 Certificate");
        }
    }

    static void verifyCertificateCommonName(X500Name subject, String commonName) {
        List<AttributeTypeAndValue> attributesAndValues = Arrays.stream(subject.getRDNs())
                .flatMap(rdn -> rdn.isMultiValued() ?
                        Stream.of(rdn.getTypesAndValues()) : Stream.of(rdn.getFirst()))
                .filter(attr -> attr.getType() == BCStyle.CN)
                .collect(Collectors.toList());

        if (attributesAndValues.size() != 1) {
            throw new IllegalArgumentException("Only 1 common name should be set");
        }

        String actualCommonName = DERUTF8String.getInstance(attributesAndValues.get(0).getValue()).getString();
        if (! actualCommonName.equals(commonName)) {
            throw new IllegalArgumentException("Expected common name to be " + commonName + ", but was " + actualCommonName);
        }
    }

    @SuppressWarnings("unchecked")
    static void verifyCertificateExtensions(PKCS10CertificationRequest request) {
        List<String> illegalExt = Arrays
                .stream(request.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest))
                .map(attribute -> Extensions.getInstance(attribute.getAttrValues().getObjectAt(0)))
                .flatMap(ext -> Collections.list((Enumeration<ASN1ObjectIdentifier>) ext.oids()).stream())
                .filter(ILLEGAL_EXTENSIONS::contains)
                .map(ASN1ObjectIdentifier::getId)
                .collect(Collectors.toList());

        if (! illegalExt.isEmpty()) {
            throw new IllegalArgumentException("CSR contains illegal extensions: " + String.join(", ", illegalExt));
        }
    }

    private static PrivateKey getPrivateKey(KeyProvider keyProvider, AthenzProviderServiceConfig config, Zone zone) {
        AthenzProviderServiceConfig.Zones zoneConfig = getZoneConfig(config, zone);
        return keyProvider.getPrivateKey(zoneConfig.secretVersion());
    }
}
