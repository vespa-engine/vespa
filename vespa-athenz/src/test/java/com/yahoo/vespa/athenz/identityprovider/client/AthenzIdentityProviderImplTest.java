// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyStoreUtils;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrBuilder;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author mortent
 * @author bjorncs
 */
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
    void component_creation_fails_when_credentials_not_found() {
        assertThrows(AthenzIdentityProviderException.class, () -> {
            AthenzCredentialsService credentialService = mock(AthenzCredentialsService.class);
            when(credentialService.registerInstance())
                    .thenThrow(new RuntimeException("athenz unavailable"));

            new AthenzIdentityProviderImpl(IDENTITY_CONFIG, mock(Metric.class), trustStoreFile, credentialService, mock(ScheduledExecutorService.class), new ManualClock(Instant.EPOCH));
        });
    }

    @Test
    void metrics_updated_on_refresh() {
        ManualClock clock = new ManualClock(Instant.EPOCH);
        Metric metric = mock(Metric.class);

        AthenzCredentialsService athenzCredentialsService = mock(AthenzCredentialsService.class);

        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        X509Certificate certificate = getCertificate(keyPair, getExpirationSupplier(clock));

        when(athenzCredentialsService.registerInstance())
                .thenReturn(new AthenzCredentials(certificate, keyPair, null));

        when(athenzCredentialsService.updateCredentials(any(), any()))
                .thenThrow(new RuntimeException("#1"))
                .thenThrow(new RuntimeException("#2"))
                .thenReturn(new AthenzCredentials(certificate, keyPair, null));

        AthenzIdentityProviderImpl identityProvider =
                new AthenzIdentityProviderImpl(IDENTITY_CONFIG, metric, trustStoreFile, athenzCredentialsService, mock(ScheduledExecutorService.class), clock);

        identityProvider.reportMetrics();
        verify(metric).set(eq(AthenzIdentityProviderImpl.CERTIFICATE_EXPIRY_METRIC_NAME), eq(certificateValidity.getSeconds()), any());

        // Advance 1 day, refresh fails, cert is 1 day old
        clock.advance(Duration.ofDays(1));
        identityProvider.refreshCertificate();
        identityProvider.reportMetrics();
        verify(metric).set(eq(AthenzIdentityProviderImpl.CERTIFICATE_EXPIRY_METRIC_NAME), eq(certificateValidity.minus(Duration.ofDays(1)).getSeconds()), any());

        // Advance 1 more day, refresh fails, cert is 2 days old
        clock.advance(Duration.ofDays(1));
        identityProvider.refreshCertificate();
        identityProvider.reportMetrics();
        verify(metric).set(eq(AthenzIdentityProviderImpl.CERTIFICATE_EXPIRY_METRIC_NAME), eq(certificateValidity.minus(Duration.ofDays(2)).getSeconds()), any());

        // Advance 1 more day, refresh succeds, cert is new
        clock.advance(Duration.ofDays(1));
        identityProvider.refreshCertificate();
        identityProvider.reportMetrics();
        verify(metric).set(eq(AthenzIdentityProviderImpl.CERTIFICATE_EXPIRY_METRIC_NAME), eq(certificateValidity.getSeconds()), any());

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
