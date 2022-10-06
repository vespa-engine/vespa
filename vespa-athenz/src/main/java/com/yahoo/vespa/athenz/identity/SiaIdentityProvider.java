// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.security.AutoReloadingX509KeyManager;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.utils.SiaUtils;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * A {@link ServiceIdentityProvider} that provides the credentials stored on file system.
 *
 * @author mortent
 * @author bjorncs
 */
public class SiaIdentityProvider extends AbstractComponent implements ServiceIdentityProvider {

    private final AutoReloadingX509KeyManager keyManager;
    private final SSLContext sslContext;
    private final Optional<SSLContext> sslContextWithPublicCerts;
    private final AthenzIdentity service;
    private final Path certificateFile;
    private final Path privateKeyFile;

    public enum TrustStorePublicCerts {
        /** The trust store includes public root certificates. */
        INCLUDED,
        EXCLUDED,
        UNKNOWN
    }

    @Inject
    public SiaIdentityProvider(SiaProviderConfig config) {
        this(new AthenzService(config.athenzDomain(), config.athenzService()),
             Paths.get(config.keyPathPrefix()),
             Paths.get(config.trustStorePath()),
             TrustStorePublicCerts.UNKNOWN);
    }

    public SiaIdentityProvider(AthenzIdentity service,
                               Path siaPath,
                               Path clientTruststoreFile,
                               TrustStorePublicCerts publicCerts) {
        this(service,
             SiaUtils.getPrivateKeyFile(siaPath, service),
             SiaUtils.getCertificateFile(siaPath, service),
             clientTruststoreFile,
             publicCerts);
    }

    SiaIdentityProvider(AthenzIdentity service,
                        Path privateKeyFile,
                        Path certificateFile,
                        Path clientTruststoreFile,
                        TrustStorePublicCerts publicCerts) {
        this.service = service;
        this.keyManager = AutoReloadingX509KeyManager.fromPemFiles(privateKeyFile, certificateFile);
        this.sslContext = createIdentitySslContext(keyManager, clientTruststoreFile);
        this.sslContextWithPublicCerts = switch (publicCerts) {
            case INCLUDED -> Optional.of(sslContext);
            case EXCLUDED -> Optional.of(createIdentitySslContextWithDefaultTrustStore(keyManager));
            case UNKNOWN -> Optional.empty();
        };
        this.certificateFile = certificateFile;
        this.privateKeyFile = privateKeyFile;
    }

    @Override
    public AthenzIdentity identity() {
        return service;
    }

    @Override
    public SSLContext getIdentitySslContext() {
        return sslContext;
    }

    @Override
    public SSLContext getIdentitySslContextForPublicPeer() {
        return sslContextWithPublicCerts.orElseThrow(UnsupportedOperationException::new);
    }

    @Override public X509CertificateWithKey getIdentityCertificateWithKey() { return keyManager.getCurrentCertificateWithKey(); }
    @Override public Path certificatePath() { return certificateFile; }
    @Override public Path privateKeyPath() { return privateKeyFile; }

    private static SSLContext createIdentitySslContext(AutoReloadingX509KeyManager keyManager, Path trustStoreFile) {
        return new SslContextBuilder()
                .withTrustStore(trustStoreFile)
                .withKeyManager(keyManager)
                .build();
    }

    private static SSLContext createIdentitySslContextWithDefaultTrustStore(AutoReloadingX509KeyManager keyManager) {
        return new SslContextBuilder()
                .withKeyManager(keyManager)
                .build();
    }

    @Override
    public void deconstruct() {
        keyManager.close();
    }

}
