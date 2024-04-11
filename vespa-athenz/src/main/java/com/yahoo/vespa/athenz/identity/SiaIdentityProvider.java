// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.TrustManagerUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.security.AutoReloadingX509KeyManager;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.utils.SiaUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.security.cert.X509Certificate;
import java.util.List;

import java.util.stream.Stream;

/**
 * A {@link ServiceIdentityProvider} that provides the credentials stored on file system.
 *
 * @author mortent
 * @author bjorncs
 */
public class SiaIdentityProvider extends AbstractComponent implements ServiceIdentityProvider {

    private final AutoReloadingX509KeyManager keyManager;
    private final SSLContext sslContext;
    private final AthenzIdentity service;
    private final Path certificateFile;
    private final Path privateKeyFile;

    @Inject
    public SiaIdentityProvider(SiaProviderConfig config) {
        this(new AthenzService(config.athenzDomain(), config.athenzService()),
             SiaUtils.getPrivateKeyFile(Paths.get(config.keyPathPrefix()), new AthenzService(config.athenzDomain(), config.athenzService())),
             SiaUtils.getCertificateFile(Paths.get(config.keyPathPrefix()), new AthenzService(config.athenzDomain(), config.athenzService())),
             Paths.get(config.trustStorePath()), config.publicSystem());
    }

    public SiaIdentityProvider(AthenzIdentity service,
                               Path siaPath,
                               Path clientTruststoreFile,
                               boolean publicSystem) {
        this(service,
                SiaUtils.getPrivateKeyFile(siaPath, service),
                SiaUtils.getCertificateFile(siaPath, service),
                clientTruststoreFile,
                publicSystem);
    }

    public SiaIdentityProvider(AthenzIdentity service,
                               Path privateKeyFile,
                               Path certificateFile,
                               Path clientTruststoreFile,
                               boolean publicSystem) {
        this.service = service;
        this.keyManager = AutoReloadingX509KeyManager.fromPemFiles(privateKeyFile, certificateFile);
        this.sslContext = createIdentitySslContext(keyManager, clientTruststoreFile, publicSystem);
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

    @Override public X509CertificateWithKey getIdentityCertificateWithKey() { return keyManager.getCurrentCertificateWithKey(); }
    @Override public Path certificatePath() { return certificateFile; }
    @Override public Path privateKeyPath() { return privateKeyFile; }

    public SSLContext createIdentitySslContextWithTrustStore(Path trustStoreFile) {
        return createIdentitySslContext(keyManager, trustStoreFile, false);
    }

    /**
     * Create an SSL context with the given trust store and the key manager from this provider.
     * If the {code includeDefaultTruststore} is true, the default trust store will be included.
     *
     * @param keyManager the key manager
     * @param trustStoreFile the trust store file
     * @param includeDefaultTruststore whether to include the default trust store
     */
    private static SSLContext createIdentitySslContext(AutoReloadingX509KeyManager keyManager, Path trustStoreFile, boolean includeDefaultTruststore) {
        List<X509Certificate> defaultTrustStore = List.of();
        if (includeDefaultTruststore) {
            try {
                // load the default java trust store and extract the certificates
                defaultTrustStore = Stream.of(TrustManagerUtils.createDefaultX509TrustManager().getAcceptedIssuers()).toList();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load default trust store", e);
            }
        }
        try {
            List<X509Certificate> caCertList = Stream.concat(
                            X509CertificateUtils.certificateListFromPem(new String(Files.readAllBytes(trustStoreFile))).stream(),
                            defaultTrustStore.stream())
                    .toList();
            return new SslContextBuilder()
                    .withTrustStore(caCertList)
                    .withKeyManager(keyManager)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deconstruct() {
        keyManager.close();
    }

}
