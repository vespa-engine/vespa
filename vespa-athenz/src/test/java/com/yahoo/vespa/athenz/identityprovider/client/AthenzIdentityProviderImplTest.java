// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProviderException;
import com.yahoo.jdisc.Metric;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.athenz.tls.KeyStoreBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
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
                                       .service("tenantService").domain("tenantDomain").loadBalancerAddress("cfg"));

    @Test(expected = AthenzIdentityProviderException.class)
    public void component_creation_fails_when_credentials_not_found() {
        AthenzCredentialsService credentialService = mock(AthenzCredentialsService.class);
        when(credentialService.registerInstance())
                .thenThrow(new RuntimeException("athenz unavailable"));

        new AthenzIdentityProviderImpl(IDENTITY_CONFIG, mock(Metric.class), credentialService, mock(ScheduledExecutorService.class), new ManualClock(Instant.EPOCH));
    }

    @Test
    public void metrics_updated_on_refresh() throws IOException {
        IdentityDocumentService identityDocumentService = mock(IdentityDocumentService.class);
        AthenzService athenzService = mock(AthenzService.class);
        ManualClock clock = new ManualClock(Instant.EPOCH);
        Metric metric = mock(Metric.class);

        when(identityDocumentService.getSignedIdentityDocument()).thenReturn(getIdentityDocument());
        when(athenzService.sendInstanceRegisterRequest(any(), any())).then(new Answer<InstanceIdentity>() {
            @Override
            public InstanceIdentity answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new InstanceIdentity(getCertificate(getExpirationSupplier(clock)), "TOKEN");
            }
        });

        when(athenzService.sendInstanceRefreshRequest(anyString(), anyString(), anyString(),
                                                      anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("#1"))
                .thenThrow(new RuntimeException("#2"))
                .thenReturn(new InstanceIdentity(getCertificate(getExpirationSupplier(clock)), "TOKEN"));

        AthenzCredentialsService credentialService =
                new AthenzCredentialsService(IDENTITY_CONFIG, identityDocumentService, athenzService, createDummyTrustStore());

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

    private static String getIdentityDocument() {
        return "{\n" +
               "  \"identity-document\": \"eyJwcm92aWRlci11bmlxdWUtaWQiOnsidGVuYW50IjoidGVuYW50IiwiYXBwbGljYXRpb24iOiJhcHBsaWNhdGlvbiIsImVudmlyb25tZW50IjoiZGV2IiwicmVnaW9uIjoidXMtbm9ydGgtMSIsImluc3RhbmNlIjoiZGVmYXVsdCIsImNsdXN0ZXItaWQiOiJkZWZhdWx0IiwiY2x1c3Rlci1pbmRleCI6MH0sImNvbmZpZ3NlcnZlci1ob3N0bmFtZSI6ImxvY2FsaG9zdCIsImluc3RhbmNlLWhvc3RuYW1lIjoieC55LmNvbSIsImNyZWF0ZWQtYXQiOjE1MDg3NDgyODUuNzQyMDAwMDAwfQ==\",\n" +
               "  \"signature\": \"kkEJB/98cy1FeXxzSjtvGH2a6BFgZu/9/kzCcAqRMZjENxnw5jyO1/bjZVzw2Sz4YHPsWSx2uxb32hiQ0U8rMP0zfA9nERIalSP0jB/hMU8laezGhdpk6VKZPJRC6YKAB9Bsv2qUIfMsSxkMqf66GUvjZAGaYsnNa2yHc1jIYHOGMeJO+HNPYJjGv26xPfAOPIKQzs3RmKrc3FoweTCsIwm5oblqekdJvVWYe0obwlOSB5uwc1zpq3Ie1QBFtJRuCGMVHg1pDPxXKBHLClGIrEvzLmICy6IRdHszSO5qiwujUD7sbrbM0sB/u0cYucxbcsGRUmBvme3UAw2mW9POVQ==\",\n" +
               "  \"signing-key-version\": 0,\n" +
               "  \"provider-unique-id\": \"tenant.application.dev.us-north-1.default.default.0\",\n" +
               "  \"dns-suffix\": \"dnsSuffix\",\n" +
               "  \"provider-service\": \"service\",\n" +
               "  \"zts-endpoint\": \"localhost/zts\", \n" +
               "  \"document-version\": 1\n" +
               "}";

    }
}
