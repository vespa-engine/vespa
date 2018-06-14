// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.test.ManualClock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author mortent
 * @author bjorncs
 */
public class AthenzIdentityProviderImplTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

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

    @Test(expected = AthenzIdentityProviderException.class)
    public void component_creation_fails_when_credentials_not_found() {
        AthenzCredentialsService credentialService = mock(AthenzCredentialsService.class);
        when(credentialService.registerInstance())
                .thenThrow(new RuntimeException("athenz unavailable"));

        new AthenzIdentityProviderImpl(IDENTITY_CONFIG, mock(Metric.class), credentialService, mock(ScheduledExecutorService.class), new ManualClock(Instant.EPOCH));
    }

    @Test
    public void metrics_updated_on_refresh() {
        ManualClock clock = new ManualClock(Instant.EPOCH);
        Metric metric = mock(Metric.class);

        AthenzCredentialsService athenzCredentialsService = mock(AthenzCredentialsService.class);

        X509Certificate certificate = getCertificate(getExpirationSupplier(clock));

        when(athenzCredentialsService.registerInstance())
                .thenReturn(new AthenzCredentials(certificate, null, null, null));

        when(athenzCredentialsService.updateCredentials(any(), any()))
                .thenThrow(new RuntimeException("#1"))
                .thenThrow(new RuntimeException("#2"))
                .thenReturn(new AthenzCredentials(certificate, null, null, null));

        AthenzIdentityProviderImpl identityProvider =
                new AthenzIdentityProviderImpl(IDENTITY_CONFIG, metric, athenzCredentialsService, mock(ScheduledExecutorService.class), clock);

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

    private X509Certificate getCertificate(Supplier<Date> expiry) {
        X509Certificate x509Certificate = mock(X509Certificate.class);
        when(x509Certificate.getNotAfter()).thenReturn(expiry.get());
        return x509Certificate;
    }

}
