// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static com.yahoo.vespa.athenz.tls.KeyStoreType.JKS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
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
    public void metrics_updated_on_refresh() throws IOException {
        IdentityDocumentClient identityDocumentClient = mock(IdentityDocumentClient.class);
        ZtsClient ztsClient = mock(ZtsClient.class);
        ManualClock clock = new ManualClock(Instant.EPOCH);
        Metric metric = mock(Metric.class);

        when(identityDocumentClient.getTenantIdentityDocument(any())).thenReturn(getIdentityDocument());
        when(ztsClient.sendInstanceRegisterRequest(any(), any())).then(new Answer<InstanceIdentity>() {
            @Override
            public InstanceIdentity answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new InstanceIdentity(getCertificate(getExpirationSupplier(clock)), "TOKEN");
            }
        });

        when(ztsClient.sendInstanceRefreshRequest(anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("#1"))
                .thenThrow(new RuntimeException("#2"))
                .thenReturn(new InstanceIdentity(getCertificate(getExpirationSupplier(clock)), "TOKEN"));

        AthenzCredentialsService credentialService =
                new AthenzCredentialsService(IDENTITY_CONFIG, identityDocumentClient, ztsClient, createDummyTrustStore(), "localhost");

        AthenzIdentityProviderImpl identityProvider =
                new AthenzIdentityProviderImpl(IDENTITY_CONFIG, metric, credentialService, mock(ScheduledExecutorService.class), clock);

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

    private File createDummyTrustStore() throws IOException {
        File file = tempDir.newFile();
        KeyStore keyStore = KeyStoreBuilder.withType(JKS).build();
        KeyStoreUtils.writeKeyStoreToFile(keyStore, file);
        return file;
    }

    private static SignedIdentityDocument getIdentityDocument() {
        VespaUniqueInstanceId instanceId = new VespaUniqueInstanceId(0, "default", "default", "application", "tenant", "us-north-1", "dev", IdentityType.TENANT);
        return new SignedIdentityDocument(
                new IdentityDocument(instanceId, "localhost", "x.y.com", Instant.EPOCH, Collections.emptySet()),
                "dummysignature",
                0,
                instanceId,
                "dev-us-north-1.vespa.cloud",
                new AthenzService("vespa.vespa.provider_dev_us-north-1"),
                URI.create("https://zts:4443/zts/v1"),
                1,
                "localhost",
                "x.y.com",
                Instant.EPOCH,
                Collections.emptySet(),
                IdentityType.TENANT);
    }
}
