package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.KeyProvider;
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


/**
 * @author freva
 */
public class CertificateSigner {

    private static final Logger log = Logger.getLogger(CertificateSigner.class.getName());

    static final String SIGNER_ALGORITHM = "SHA256withRSA";
    static final Duration CERTIIFICATE_DURATION = Duration.ofDays(30);
    private static final List<ASN1ObjectIdentifier> ILLEGAL_EXTENSIONS = Arrays.asList(
            Extension.basicConstraints, Extension.subjectAlternativeName);

    private final PrivateKey caPrivateKey;
    private final X500Name issuer;
    private final Clock clock;

    public CertificateSigner(KeyProvider keyProvider,
                             AthenzProviderServiceConfig.Zones zoneConfig,
                             String configServerHostname) {
        this(keyProvider.getPrivateKey(zoneConfig.secretVersion()), configServerHostname, Clock.systemUTC());
    }

    CertificateSigner(PrivateKey caPrivateKey, String configServerHostname, Clock clock) {
        this.caPrivateKey = caPrivateKey;
        this.issuer = new X500Name("CN=" + configServerHostname);
        this.clock = clock;
    }

    X509Certificate generateX509Certificate(PKCS10CertificationRequest certReq, String remoteHostname) {
        assertCertificateCommonName(certReq.getSubject(), remoteHostname);
        assertCertificateExtensions(certReq);

        Date notBefore = Date.from(clock.instant());
        Date notAfter = Date.from(clock.instant().plus(CERTIIFICATE_DURATION));

        try {
            PublicKey publicKey = new JcaPKCS10CertificationRequest(certReq).getPublicKey();
            X509v3CertificateBuilder caBuilder = new JcaX509v3CertificateBuilder(
                    issuer, BigInteger.valueOf(clock.millis()), notBefore, notAfter, certReq.getSubject(), publicKey)

                    // Set Basic Constraints to false
                    .addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

            ContentSigner caSigner = new JcaContentSignerBuilder(SIGNER_ALGORITHM).build(caPrivateKey);

            return new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider())
                    .getCertificate(caBuilder.build(caSigner));
        } catch (Exception ex) {
            log.log(LogLevel.ERROR, "Failed to generate X509 Certificate", ex);
            throw new RuntimeException("Failed to generate X509 Certificate");
        }
    }

    static void assertCertificateCommonName(X500Name subject, String commonName) {
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
    static void assertCertificateExtensions(PKCS10CertificationRequest request) {
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
}
