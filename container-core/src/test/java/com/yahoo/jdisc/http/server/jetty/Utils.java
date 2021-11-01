// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Module;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.Pkcs10CsrBuilder;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;

/**
 * @author bjorncs
 */
class Utils {

    private Utils() {}

    static JettyTestDriver createSslTestDriver(
            Path serverCertificateFile, Path serverPrivateKeyFile, MetricConsumerMock metricConsumer, InMemoryConnectionLog connectionLog) {
        Module extraModule = binder -> {
            binder.bind(MetricConsumer.class).toInstance(metricConsumer.mockitoMock());
            binder.bind(ConnectionLog.class).toInstance(connectionLog);
        };
        return JettyTestDriver.newInstanceWithSsl(
                new EchoRequestHandler(), serverCertificateFile, serverPrivateKeyFile, JettyTestDriver.TlsClientAuth.NEED, extraModule);
    }

    static void generatePrivateKeyAndCertificate(Path privateKeyFile, Path certificateFile) throws IOException {
        KeyPair keyPair = KeyUtils.generateKeypair(EC);
        Files.writeString(privateKeyFile, KeyUtils.toPem(keyPair.getPrivate()));

        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(
                        keyPair, new X500Principal("CN=localhost"), Instant.EPOCH, Instant.EPOCH.plus(100_000, ChronoUnit.DAYS), SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
        Files.writeString(certificateFile, X509CertificateUtils.toPem(certificate));
    }

    static void generatePrivateKeyAndCertificate(Path rootPrivateKeyFile, Path rootCertificateFile,
                                                 Path privateKeyFile, Path certificateFile, Instant notAfter) throws IOException {
        X509Certificate rootCertificate = X509CertificateUtils.fromPem(Files.readString(rootCertificateFile));
        PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(Files.readString(rootPrivateKeyFile));

        KeyPair keyPair = KeyUtils.generateKeypair(EC);
        Files.writeString(privateKeyFile, KeyUtils.toPem(keyPair.getPrivate()));
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(new X500Principal("CN=myclient"), keyPair, SHA256_WITH_ECDSA).build();
        X509Certificate certificate = X509CertificateBuilder
                .fromCsr(csr, rootCertificate.getSubjectX500Principal(), Instant.EPOCH, notAfter, privateKey, SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
        Files.writeString(certificateFile, X509CertificateUtils.toPem(certificate));
    }

    static CloseableHttpAsyncClient createHttp2Client(JettyTestDriver driver) {
        TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                .setSslContext(driver.sslContext())
                .build();
        var client = H2AsyncClientBuilder.create()
                .disableAutomaticRetries()
                .setTlsStrategy(tlsStrategy)
                .build();
        client.start();
        return client;
    }
}
