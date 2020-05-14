// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.security.tls.AutoReloadingX509KeyManager;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.utils.SiaUtils;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.nio.file.Paths;

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
             Paths.get(config.trustStorePath()),
             config.trustStoreType());
    }

    public SiaIdentityProvider(AthenzIdentity service,
                               Path siaPath,
                               Path trustStoreFile) {
        this(service,
             SiaUtils.getPrivateKeyFile(siaPath, service),
             SiaUtils.getCertificateFile(siaPath, service),
             trustStoreFile,
             SiaProviderConfig.TrustStoreType.Enum.jks);
    }

    public SiaIdentityProvider(AthenzIdentity service,
                               Path privateKeyFile,
                               Path certificateFile,
                               Path trustStoreFile,
                               SiaProviderConfig.TrustStoreType.Enum trustStoreType) {
        this.service = service;
        this.keyManager = AutoReloadingX509KeyManager.fromPemFiles(privateKeyFile, certificateFile);
        this.sslContext = createIdentitySslContext(keyManager, trustStoreFile, trustStoreType);
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

    private static SSLContext createIdentitySslContext(AutoReloadingX509KeyManager keyManager, Path trustStoreFile,
                                                       SiaProviderConfig.TrustStoreType.Enum trustStoreType) {
        var builder = new SslContextBuilder();
        if (trustStoreType == SiaProviderConfig.TrustStoreType.Enum.pem) {
            builder = builder.withTrustStore(trustStoreFile);
        } else if (trustStoreType == SiaProviderConfig.TrustStoreType.Enum.jks) {
            builder = builder.withTrustStore(trustStoreFile, KeyStoreType.JKS);
        } else {
            throw new IllegalArgumentException("Unsupported trust store type: " + trustStoreType);
        }
        return builder.withKeyManager(keyManager).build();
    }

    @Override
    public void deconstruct() {
        keyManager.close();
    }

}
