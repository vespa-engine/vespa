package com.yahoo.security;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * An {@link SSLContext} with its associated {@link X509ExtendedTrustManager} and {@link X509ExtendedKeyManager}.
 *
 * @author bjorncs
 */
public record X509SslContext(SSLContext context, X509ExtendedTrustManager trustManager, X509ExtendedKeyManager keyManager) {
    private static final X509SslContext DEFAULT_INSTANCE = new SslContextBuilder().buildContext();

    /** @return the default instance of {@link X509SslContext} */
    public static X509SslContext getDefault() { return DEFAULT_INSTANCE; }
}
