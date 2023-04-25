// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.jdisc.Metric;
import com.yahoo.security.AutoReloadingX509KeyManager;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyStoreUtils;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrBuilder;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.test.ManualClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AthenzIdentityProviderImplTest {

    @TempDir
    public File tempDir;

    public static final Duration certificateValidity = Duration.ofDays(30);

    private static final IdentityConfig IDENTITY_CONFIG =
            new IdentityConfig(new IdentityConfig.Builder()
                                       .service("tenantService")
                                       .domain("tenantDomain")
                                       .nodeIdentityName("vespa.tenant")
                                       .configserverIdentityName("vespa.configserver")
                                       .loadBalancerAddress("cfg")
                                       .ztsUrl("https:localhost:4443/zts/v1")
                                       .athenzDnsSuffix("dev-us-north-1.vespa.cloud"));

    private final KeyPair caKeypair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
    private Path trustStoreFile;
    private X509Certificate caCertificate;

    @BeforeEach
    public void createTrustStoreFile() throws IOException {
        caCertificate = X509CertificateBuilder
                .fromKeypair(
                        caKeypair,
                        new X500Principal("CN=mydummyca"),
                        Instant.EPOCH,
                        Instant.EPOCH.plus(10000, ChronoUnit.DAYS),
                        SignatureAlgorithm.SHA256_WITH_ECDSA,
                        BigInteger.ONE)
                .build();
        trustStoreFile = File.createTempFile("junit", null, tempDir).toPath();
        KeyStoreUtils.writeKeyStoreToFile(
                KeyStoreBuilder.withType(KeyStoreType.JKS)
                        .withKeyEntry("default", caKeypair.getPrivate(), caCertificate)
                        .build(),
                trustStoreFile);
    }

    @Test
    void certificate_expiry_metric_is_reported() {
        ManualClock clock = new ManualClock(Instant.EPOCH);
        Metric metric = mock(Metric.class);
        AutoReloadingX509KeyManager keyManager = mock(AutoReloadingX509KeyManager.class);
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        X509Certificate certificate = getCertificate(keyPair, getExpirationSupplier(clock));
        when(keyManager.getCurrentCertificateWithKey()).thenReturn(new X509CertificateWithKey(certificate, keyPair.getPrivate()));

        AthenzIdentityProviderImpl identityProvider = new AthenzIdentityProviderImpl(IDENTITY_CONFIG, metric, trustStoreFile, mock(ScheduledExecutorService.class), clock, keyManager);
        identityProvider.reportMetrics();
        verify(metric).set(eq(AthenzIdentityProviderImpl.CERTIFICATE_EXPIRY_METRIC_NAME), eq(certificateValidity.getSeconds()), any());

        clock.advance(Duration.ofDays(1));
        identityProvider.reportMetrics();
        verify(metric).set(eq(AthenzIdentityProviderImpl.CERTIFICATE_EXPIRY_METRIC_NAME), eq(certificateValidity.minus(Duration.ofDays(1)).getSeconds()), any());

        clock.advance(Duration.ofDays(1));
        identityProvider.reportMetrics();
        verify(metric).set(eq(AthenzIdentityProviderImpl.CERTIFICATE_EXPIRY_METRIC_NAME), eq(certificateValidity.minus(Duration.ofDays(2)).getSeconds()), any());
    }

    private Supplier<Date> getExpirationSupplier(ManualClock clock) {
        return () -> new Date(clock.instant().plus(certificateValidity).toEpochMilli());
    }

    private X509Certificate getCertificate(KeyPair keyPair, Supplier<Date> expiry) {
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(new X500Principal("CN=dummy"), keyPair, SignatureAlgorithm.SHA256_WITH_ECDSA)
                .build();
        return X509CertificateBuilder
                .fromCsr(csr,
                         caCertificate.getSubjectX500Principal(),
                         Instant.EPOCH,
                         expiry.get().toInstant(),
                         caKeypair.getPrivate(),
                         SignatureAlgorithm.SHA256_WITH_ECDSA,
                         BigInteger.ONE)
                .build();
    }

}
