// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.authz.PeerAuthorizerTrustManager;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link TlsContext} that uses the tls configuration specified in the transport security options file.
 * The credentials are regularly reloaded to support short-lived certificates.
 *
 * @author bjorncs
 */
public class ConfigFileBasedTlsContext implements TlsContext {

    private final TlsContext tlsContext;
    private TlsManager tlsManager;

    static private final Map<Path, WeakReference<TlsManager>> trustManagers = new HashMap<>();

    private static TlsManager getOrCreateTrustManager(Path tlsOptionsConfigFile) {
        synchronized (trustManagers) {
            WeakReference<TlsManager> tlsManager = trustManagers.get(tlsOptionsConfigFile);
            if (tlsManager == null || tlsManager.get() == null) {
                TlsManager manager = new TlsManager(tlsOptionsConfigFile);
                trustManagers.put(tlsOptionsConfigFile, new WeakReference<>(manager));
                return manager;
            }
            return tlsManager.get();
        }
    }

    public ConfigFileBasedTlsContext(Path tlsOptionsConfigFile, AuthorizationMode mode) {
        this(tlsOptionsConfigFile, mode, PeerAuthentication.NEED);
    }

    /**
     * Allows the caller to override the default peer authentication mode. This is only intended to be used in situations where
     * the TLS peer authentication is enforced at a higher protocol or application layer (e.g with {@link PeerAuthentication#WANT}).
     */
    public ConfigFileBasedTlsContext(Path tlsOptionsConfigFile, AuthorizationMode mode, PeerAuthentication peerAuthentication) {
        tlsManager = getOrCreateTrustManager(tlsOptionsConfigFile);
        this.tlsContext = createDefaultTlsContext(tlsManager.getOptions(), mode, tlsManager.getTrustManager(), tlsManager.getKeyManager(), peerAuthentication);
    }

    // Wrapped methods from TlsContext
    @Override public SSLContext context() { return tlsContext.context(); }
    @Override public SSLParameters parameters() { return tlsContext.parameters(); }
    @Override public SSLEngine createSslEngine() { return tlsContext.createSslEngine(); }
    @Override public SSLEngine createSslEngine(String peerHost, int peerPort) { return tlsContext.createSslEngine(peerHost, peerPort); }

    private static DefaultTlsContext createDefaultTlsContext(TransportSecurityOptions options,
                                                             AuthorizationMode mode,
                                                             MutableX509TrustManager mutableTrustManager,
                                                             MutableX509KeyManager mutableKeyManager,
                                                             PeerAuthentication peerAuthentication) {

        HostnameVerification hostnameVerification = options.isHostnameValidationDisabled() ? HostnameVerification.DISABLED : HostnameVerification.ENABLED;
        PeerAuthorizerTrustManager authorizerTrustManager = options.getAuthorizedPeers()
                .map(authorizedPeers -> new PeerAuthorizerTrustManager(authorizedPeers, mode, hostnameVerification, mutableTrustManager))
                .orElseGet(() -> new PeerAuthorizerTrustManager(new AuthorizedPeers(com.yahoo.vespa.jdk8compat.Set.of()), AuthorizationMode.DISABLE, hostnameVerification, mutableTrustManager));
        SSLContext sslContext = new SslContextBuilder()
                .withKeyManager(mutableKeyManager)
                .withTrustManager(authorizerTrustManager)
                .build();
        List<String> acceptedCiphers = options.getAcceptedCiphers();
        Set<String> ciphers = acceptedCiphers.isEmpty() ? TlsContext.ALLOWED_CIPHER_SUITES : new HashSet<>(acceptedCiphers);
        return new DefaultTlsContext(sslContext, ciphers, peerAuthentication);
    }

}
