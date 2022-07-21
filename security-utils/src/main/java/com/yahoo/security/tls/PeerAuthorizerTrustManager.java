// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.TrustManagerUtils;
import com.yahoo.security.X509CertificateUtils;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link X509ExtendedTrustManager} that performs additional certificate verification through {@link PeerAuthorizer}.
 *
 * Implementation assumes that provided {@link X509ExtendedTrustManager} will throw {@link IllegalArgumentException}
 * when chain is empty or null.
 *
 * @author bjorncs
 */
class PeerAuthorizerTrustManager extends X509ExtendedTrustManager {

    static final String AUTH_CONTEXT_PROPERTY = "vespa.tls.auth.ctx";

    private static final Logger log = Logger.getLogger(PeerAuthorizerTrustManager.class.getName());

    private final PeerAuthorizer authorizer;
    private final X509ExtendedTrustManager defaultTrustManager;
    private final AuthorizationMode mode;
    private final HostnameVerification hostnameVerification;

    PeerAuthorizerTrustManager(AuthorizedPeers authorizedPeers, AuthorizationMode mode,
                               HostnameVerification hostnameVerification, X509ExtendedTrustManager defaultTrustManager) {
        this.authorizer = new PeerAuthorizer(authorizedPeers);
        this.mode = mode;
        this.hostnameVerification = hostnameVerification;
        this.defaultTrustManager = defaultTrustManager;
    }

    PeerAuthorizerTrustManager(AuthorizedPeers authorizedPeers, AuthorizationMode mode,
                               HostnameVerification hostnameVerification, KeyStore truststore) {
        this(authorizedPeers, mode, hostnameVerification, TrustManagerUtils.createDefaultX509TrustManager(truststore));
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType);
        authorizePeer(chain, authType, true, null);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        defaultTrustManager.checkServerTrusted(chain, authType);
        authorizePeer(chain, authType, false, null);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType, socket);
        authorizePeer(chain, authType, true, ((SSLSocket)socket).getHandshakeSession());
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        overrideHostnameVerificationForClient(socket);
        defaultTrustManager.checkServerTrusted(chain, authType, socket);
        authorizePeer(chain, authType, false, ((SSLSocket)socket).getHandshakeSession());
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType, sslEngine);
        authorizePeer(chain, authType, true, sslEngine.getHandshakeSession());
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        overrideHostnameVerificationForClient(sslEngine);
        defaultTrustManager.checkServerTrusted(chain, authType, sslEngine);
        authorizePeer(chain, authType, false, sslEngine.getHandshakeSession());
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager.getAcceptedIssuers();
    }

    private void authorizePeer(X509Certificate[] certChain, String authType, boolean isVerifyingClient,
                               SSLSession handshakeSessionOrNull) throws PeerAuthorizationFailedException {
        log.fine(() -> "Verifying certificate: " + createInfoString(certChain[0], authType, isVerifyingClient));
        ConnectionAuthContext result = mode != AuthorizationMode.DISABLE
                ? authorizer.authorizePeer(List.of(certChain))
                : ConnectionAuthContext.defaultAllCapabilities(List.of(certChain));
        if (handshakeSessionOrNull != null) {
            handshakeSessionOrNull.putValue(AUTH_CONTEXT_PROPERTY, result);
        } else {
            log.log(Level.FINE,
                    () -> "Warning: unable to provide ConnectionAuthContext as no SSLSession is available");
        }
        if (result.authorized()) {
            log.fine(() -> String.format("Verification result: %s", result));
        } else {
            String errorMessage = "Authorization failed: " + createInfoString(certChain[0], authType, isVerifyingClient);
            log.warning(errorMessage);
            if (mode == AuthorizationMode.ENFORCE) {
                throw new PeerAuthorizationFailedException(errorMessage, List.of(certChain));
            }
        }
    }

    private String createInfoString(X509Certificate certificate, String authType, boolean isVerifyingClient) {
        return String.format("DN='%s', SANs=%s, authType='%s', isVerifyingClient='%b', mode=%s",
                certificate.getSubjectX500Principal(), X509CertificateUtils.getSubjectAlternativeNames(certificate),
                authType, isVerifyingClient, mode);
    }

    private void overrideHostnameVerificationForClient(SSLEngine engine) {
        SSLParameters params = engine.getSSLParameters();
        if (overrideHostnameVerificationForClient(params)) {
            engine.setSSLParameters(params);
        }
    }

    private void overrideHostnameVerificationForClient(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSocket sslSocket = (SSLSocket) socket;
            SSLParameters params = sslSocket.getSSLParameters();
            if (overrideHostnameVerificationForClient(params)) {
                sslSocket.setSSLParameters(params);
            }
        }
    }

    // Overrides the endpoint identification algorithm specified in the ssl parameters of the ssl engine/socket.
    // The underlying trust manager will perform hostname verification if endpoint identification algorithm is set to 'HTTPS'.
    // Returns true if the parameter instance was modified
    private boolean overrideHostnameVerificationForClient(SSLParameters params) {
        String configuredAlgorithm = params.getEndpointIdentificationAlgorithm();
        switch (hostnameVerification) {
            case ENABLED:
                if (!"HTTPS".equals(configuredAlgorithm)) {
                    params.setEndpointIdentificationAlgorithm("HTTPS");
                    return true;
                }
                return false;
            case DISABLED:
                if (configuredAlgorithm != null && !configuredAlgorithm.isEmpty()) {
                    params.setEndpointIdentificationAlgorithm(""); // disable any configured endpoint identification algorithm
                    return true;
                }
                return false;
            default:
                throw new IllegalStateException("Unknown host verification type: " + hostnameVerification);
        }
    }

}
