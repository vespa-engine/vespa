// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.tls.TransportSecurityOptions;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * A {@link CryptoSocket} that creates {@link TlsCryptoSocket} instances.
 *
 * @author bjorncs
 */
public class TlsCryptoEngine implements CryptoEngine {

    private final SSLContext sslContext;

    public TlsCryptoEngine(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public TlsCryptoEngine(TransportSecurityOptions options) {
        this(createSslContext(options));
    }

    @Override
    public TlsCryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer)  {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setNeedClientAuth(true);
        sslEngine.setUseClientMode(!isServer);
        return new TlsCryptoSocket(channel, sslEngine);
    }

    private static SSLContext createSslContext(TransportSecurityOptions options) {
        return new SslContextBuilder()
                .withTrustStore(options.getCaCertificatesFile())
                .withKeyStore(options.getPrivateKeyFile(), options.getCertificatesFile())
                .build();
    }
}
