// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.tls.Extension;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.SignatureAlgorithm;
import com.yahoo.vespa.athenz.tls.X509CertificateBuilder;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.KeyProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import javax.security.auth.x500.X500Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    static final SignatureAlgorithm SIGNER_ALGORITHM = SignatureAlgorithm.SHA256_WITH_RSA;
    static final Duration CERTIFICATE_EXPIRATION = Duration.ofDays(30);
    private static final List<Extension> ILLEGAL_EXTENSIONS = ImmutableList.of(
            Extension.BASIC_CONSTRAINS, Extension.SUBJECT_ALTERNATIVE_NAMES);

    private final PrivateKey caPrivateKey;
    private final X500Principal issuer;
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
        this.issuer = new X500Principal("CN=" + loadBalancerAddress);
        this.clock = clock;
    }

    /**
     * Signs the CSR if:
     * <ul>
     *  <li>Common Name matches {@code remoteHostname}</li>
     *  <li>CSR does not contain any any of the extensions in {@code ILLEGAL_EXTENSIONS}</li>
     * </ul>
     */
    X509Certificate generateX509Certificate(Pkcs10Csr csr, String remoteHostname) {
        verifyCertificateCommonName(csr.getSubject(), remoteHostname);
        verifyCertificateExtensions(csr);

        Instant now = clock.instant();
        try {
            return X509CertificateBuilder.fromCsr(csr, issuer, now, now.plus(CERTIFICATE_EXPIRATION), caPrivateKey, SIGNER_ALGORITHM, now.toEpochMilli())
                    .setBasicConstraints(true, false)
                    .build();
        } catch (Exception ex) {
            log.log(LogLevel.ERROR, "Failed to generate X509 Certificate", ex);
            throw new RuntimeException("Failed to generate X509 Certificate", ex);
        }
    }

    static void verifyCertificateCommonName(X500Principal subject, String remoteHostname) {
        List<String> commonNames = X509CertificateUtils.getCommonNames(subject);
        if (commonNames.size() != 1) {
            throw new IllegalArgumentException("Only 1 common name should be set");
        }

        String actualCommonName = commonNames.get(0);
        if (! actualCommonName.equals(remoteHostname)) {
            throw new IllegalArgumentException("Remote hostname " + remoteHostname +
                    " does not match common name " + actualCommonName);
        }
    }

    @SuppressWarnings("unchecked")
    static void verifyCertificateExtensions(Pkcs10Csr csr) {
        List<String> extensionOIds = csr.getExtensionOIds();
        List<String> illegalExt = ILLEGAL_EXTENSIONS.stream()
                .map(Extension::getOId)
                .filter(extensionOIds::contains)
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
