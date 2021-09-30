// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

/**
 * @author bjorncs
 */
public class AutoReloadingX509KeyManagerTest {
    private static final X500Principal SUBJECT = new X500Principal("CN=dummy");

    @Rule
    public TemporaryFolder tempDirectory = new TemporaryFolder();

    @Test
    public void crypto_material_is_reloaded_when_scheduler_task_is_executed() throws IOException {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        Path privateKeyFile = tempDirectory.newFile().toPath();
        Files.write(privateKeyFile, KeyUtils.toPem(keyPair.getPrivate()).getBytes());

        Path certificateFile = tempDirectory.newFile().toPath();
        BigInteger serialNumberInitialCertificate = BigInteger.ONE;
        X509Certificate initialCertificate = generateCertificate(keyPair, serialNumberInitialCertificate);
        Files.write(certificateFile, X509CertificateUtils.toPem(initialCertificate).getBytes());

        ScheduledExecutorService scheduler = Mockito.mock(ScheduledExecutorService.class);
        ArgumentCaptor<Runnable> updaterTaskCaptor = ArgumentCaptor.forClass(Runnable.class);

        AutoReloadingX509KeyManager keyManager = new AutoReloadingX509KeyManager(privateKeyFile, certificateFile, scheduler);
        verify(scheduler).scheduleAtFixedRate(updaterTaskCaptor.capture(), anyLong(), anyLong(), any());

        String[] initialAliases = keyManager.getClientAliases(keyPair.getPublic().getAlgorithm(), new Principal[]{SUBJECT});
        X509Certificate[] certChain = keyManager.getCertificateChain(initialAliases[0]);
        assertThat(certChain).hasSize(1);
        assertThat(certChain[0].getSerialNumber()).isEqualTo(serialNumberInitialCertificate);

        BigInteger serialNumberUpdatedCertificate = BigInteger.TEN;
        X509Certificate updatedCertificate = generateCertificate(keyPair, serialNumberUpdatedCertificate);
        Files.write(certificateFile, X509CertificateUtils.toPem(updatedCertificate).getBytes());

        updaterTaskCaptor.getValue().run(); // run update task in ReloadingX509KeyManager

        String[] updatedAliases = keyManager.getClientAliases(keyPair.getPublic().getAlgorithm(), new Principal[]{SUBJECT});
        X509Certificate[] updatedCertChain = keyManager.getCertificateChain(updatedAliases[0]);
        assertThat(updatedCertChain).hasSize(1);
        assertThat(updatedCertChain[0].getSerialNumber()).isEqualTo(serialNumberUpdatedCertificate);
    }

    private static X509Certificate generateCertificate(KeyPair keyPair, BigInteger serialNumber) {
        return X509CertificateBuilder.fromKeypair(keyPair,
                                                  SUBJECT,
                                                  Instant.EPOCH,
                                                  Instant.EPOCH.plus(1, DAYS),
                                                  SignatureAlgorithm.SHA256_WITH_ECDSA,
                                                  serialNumber)
                .build();
    }
}