// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.TransportSecurityOptions;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * A {@link CryptoSocket} that creates {@link TlsCryptoSocket} instances.
 *
 * @author bjorncs
 */
public class TlsCryptoEngine implements CryptoEngine {

    private static final Logger log = Logger.getLogger(TlsCryptoEngine.class.getName());

    private final SSLContext sslContext;
    private final List<String> acceptedCiphers;

    public TlsCryptoEngine(SSLContext sslContext) {
        this(sslContext, Collections.emptyList());
    }

    public TlsCryptoEngine(SSLContext sslContext, List<String> acceptedCiphers) {
        this.sslContext = sslContext;
        this.acceptedCiphers = acceptedCiphers;
    }

    public TlsCryptoEngine(TransportSecurityOptions options) {
        this(createSslContext(options), options.getAcceptedCiphers());
    }

    @Override
    public TlsCryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer)  {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        log.fine(() -> String.format("Supported ciphers: %s", Arrays.toString(sslEngine.getSupportedCipherSuites())));
        sslEngine.setNeedClientAuth(true);
        sslEngine.setUseClientMode(!isServer);
        if (!acceptedCiphers.isEmpty()) {
            sslEngine.setEnabledCipherSuites(acceptedCiphers.toArray(new String[0]));
        }
        return new TlsCryptoSocket(channel, sslEngine);
    }

    private static SSLContext createSslContext(TransportSecurityOptions options) {
        return new SslContextBuilder()
                .withTrustStore(options.getCaCertificatesFile())
                .withKeyStore(options.getPrivateKeyFile(), options.getCertificatesFile())
                .build();
    }
}
