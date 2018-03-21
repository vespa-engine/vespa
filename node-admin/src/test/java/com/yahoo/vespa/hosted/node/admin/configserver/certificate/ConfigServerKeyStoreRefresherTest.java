// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.certificate;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.athenz.tls.X509CertificateBuilder;
import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApi;
import com.yahoo.vespa.hosted.node.admin.util.KeyStoreOptions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class ConfigServerKeyStoreRefresherTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final ManualClock clock = new ManualClock();
    private final String commonName = "CertificateRefresherTest";
    private final Duration certificateExpiration = Duration.ofDays(6);
    private final ConfigServerApi configServerApi = mock(ConfigServerApi.class);
    private final Runnable keyStoreUpdatedCallback = mock(Runnable.class);
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private KeyStoreOptions keyStoreOptions;

    @Before
    public void setup() {
        keyStoreOptions = new KeyStoreOptions(
                tempFolder.getRoot().toPath().resolve("some/path/keystore.p12"), new char[0], "PKCS12");
    }

    @Test
    public void manually_trigger_certificate_refresh() throws Exception {
        X509Certificate firstCertificate = mockConfigServerCertificateSigning(1);

        ConfigServerKeyStoreRefresher keyStoreRefresher = new ConfigServerKeyStoreRefresher(
                keyStoreOptions, keyStoreUpdatedCallback, configServerApi, executor, clock, commonName);

        // No keystore previously existed, so a new one should be written
        assertTrue(keyStoreRefresher.refreshKeyStoreIfNeeded());
        assertEquals(firstCertificate, keyStoreRefresher.getConfigServerCertificate());

        // Calling it again before a third of certificate lifetime has passed has no effect
        assertFalse(keyStoreRefresher.refreshKeyStoreIfNeeded());
        assertEquals(firstCertificate, keyStoreRefresher.getConfigServerCertificate());

        // After a third of the expiration time passes, we should refresh the certificate
        clock.advance(certificateExpiration.dividedBy(3).plusSeconds(1));
        X509Certificate secondCertificate = mockConfigServerCertificateSigning(2);
        assertTrue(keyStoreRefresher.refreshKeyStoreIfNeeded());
        assertEquals(secondCertificate, keyStoreRefresher.getConfigServerCertificate());

        verify(configServerApi, times(2))
                .post(eq(ConfigServerKeyStoreRefresher.CONFIG_SERVER_CERTIFICATE_SIGNING_PATH), any(), any());

        // We're just triggering refresh manually, so callback and executor should not have been touched
        verifyZeroInteractions(keyStoreUpdatedCallback);
        verifyZeroInteractions(executor);
    }

    @Test
    public void certificate_refresh_schedule_test() throws Exception {
        ConfigServerKeyStoreRefresher keyStoreRefresher = new ConfigServerKeyStoreRefresher(
                keyStoreOptions, keyStoreUpdatedCallback, configServerApi, executor, clock, commonName);

        // No keystore exist, so refresh once
        mockConfigServerCertificateSigning(1);
        assertTrue(keyStoreRefresher.refreshKeyStoreIfNeeded());

        // Start automatic refreshment, since keystore was just written, next check should be in 1/3rd of
        // certificate lifetime, which is in 2 days.
        keyStoreRefresher.start();
        Duration nextExpectedExecution = Duration.ofDays(2);
        verify(executor, times(1)).schedule(any(Runnable.class), eq(nextExpectedExecution.getSeconds()), eq(TimeUnit.SECONDS));

        // First automatic refreshment goes without any problems
        clock.advance(nextExpectedExecution);
        mockConfigServerCertificateSigning(2);
        keyStoreRefresher.refresh();
        verify(executor, times(2)).schedule(any(Runnable.class), eq(nextExpectedExecution.getSeconds()), eq(TimeUnit.SECONDS));
        verify(keyStoreUpdatedCallback).run();

        // We fail to refresh the certificate, wait minimum amount of time and try again
        clock.advance(nextExpectedExecution);
        mockConfigServerCertificateSigningFailure(new RuntimeException());
        keyStoreRefresher.refresh();
        nextExpectedExecution = Duration.ofSeconds(ConfigServerKeyStoreRefresher.MINIMUM_SECONDS_BETWEEN_REFRESH_RETRY);
        verify(executor, times(1)).schedule(any(Runnable.class), eq(nextExpectedExecution.getSeconds()), eq(TimeUnit.SECONDS));

        clock.advance(nextExpectedExecution);
        keyStoreRefresher.refresh();
        verify(executor, times(2)).schedule(any(Runnable.class), eq(nextExpectedExecution.getSeconds()), eq(TimeUnit.SECONDS));
        verifyNoMoreInteractions(keyStoreUpdatedCallback); // Callback not called after the last 2 failures

        clock.advance(nextExpectedExecution);
        mockConfigServerCertificateSigning(3);
        keyStoreRefresher.refresh();
        nextExpectedExecution = Duration.ofDays(2);
        verify(executor, times(3)).schedule(any(Runnable.class), eq(nextExpectedExecution.getSeconds()), eq(TimeUnit.SECONDS));
        verify(keyStoreUpdatedCallback, times(2)).run();
    }

    private X509Certificate mockConfigServerCertificateSigning(int serial) throws Exception {
        X509Certificate certificate = makeCertificate(serial);

        when(configServerApi.post(eq(ConfigServerKeyStoreRefresher.CONFIG_SERVER_CERTIFICATE_SIGNING_PATH), any(), any()))
                .thenReturn(new CertificateSerializedPayload(certificate));
        return certificate;
    }

    private void mockConfigServerCertificateSigningFailure(Exception exception) throws Exception {
        when(configServerApi.post(eq(ConfigServerKeyStoreRefresher.CONFIG_SERVER_CERTIFICATE_SIGNING_PATH), any(), any()))
                .thenThrow(exception);
    }

    private X509Certificate makeCertificate(int serial) {
        KeyPair keyPair = ConfigServerKeyStoreRefresher.generateKeyPair();
        X500Principal subject = new X500Principal("CN=" + commonName);
        Instant notBefore = clock.instant();
        Instant notAfter = clock.instant().plus(certificateExpiration);

        return X509CertificateBuilder.fromKeypair(keyPair, subject, notBefore, notAfter, ConfigServerKeyStoreRefresher.SIGNER_ALGORITHM, serial)
                .build();
    }
}