// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.authz;

import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.tls.AuthorizationMode;
import com.yahoo.security.tls.HostnameVerification;
import com.yahoo.security.tls.TrustManagerUtils;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A {@link X509ExtendedTrustManager} that performs additional certificate verification through {@link PeerAuthorizer}.
 *
 * @author bjorncs
 */
// Note: Implementation assumes that provided X509ExtendedTrustManager will throw IllegalArgumentException when chain is empty or null
public class PeerAuthorizerTrustManager extends X509ExtendedTrustManager {

    public static final String HANDSHAKE_SESSION_AUTHZ_RESULT_PROPERTY = "vespa.tls.authorization.result";

    private static final Logger log = Logger.getLogger(PeerAuthorizerTrustManager.class.getName());

    private final PeerAuthorizer authorizer;
    private final X509ExtendedTrustManager defaultTrustManager;
    private final AuthorizationMode mode;
    private final HostnameVerification hostnameVerification;

    public PeerAuthorizerTrustManager(AuthorizedPeers authorizedPeers,
                                      AuthorizationMode mode,
                                      HostnameVerification hostnameVerification,
                                      X509ExtendedTrustManager defaultTrustManager) {
        this.authorizer = new PeerAuthorizer(authorizedPeers);
        this.mode = mode;
        this.hostnameVerification = hostnameVerification;
        this.defaultTrustManager = defaultTrustManager;
    }

    public PeerAuthorizerTrustManager(AuthorizedPeers authorizedPeers,
                                      AuthorizationMode mode,
                                      HostnameVerification hostnameVerification,
                                      KeyStore truststore) {
        this(authorizedPeers, mode, hostnameVerification, TrustManagerUtils.createDefaultX509TrustManager(truststore));
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType);
        authorizePeer(chain[0], authType, true, null);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        defaultTrustManager.checkServerTrusted(chain, authType);
        authorizePeer(chain[0], authType, false, null);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType, socket);
        authorizePeer(chain[0], authType, true, null);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        overrideHostnameVerificationForClient(socket);
        defaultTrustManager.checkServerTrusted(chain, authType, socket);
        authorizePeer(chain[0], authType, false, null);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType, sslEngine);
        authorizePeer(chain[0], authType, true, sslEngine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        overrideHostnameVerificationForClient(sslEngine);
        defaultTrustManager.checkServerTrusted(chain, authType, sslEngine);
        authorizePeer(chain[0], authType, false, sslEngine);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager.getAcceptedIssuers();
    }

    /**
     * Note: The authorization result is only available during handshake. The underlying handshake session is removed once handshake is complete.
     */
    public static Optional<AuthorizationResult> getAuthorizationResult(SSLEngine sslEngine) {
        return Optional.ofNullable(sslEngine.getHandshakeSession())
                .flatMap(session -> Optional.ofNullable((AuthorizationResult) session.getValue(HANDSHAKE_SESSION_AUTHZ_RESULT_PROPERTY)));
    }

    private void authorizePeer(X509Certificate certificate, String authType, boolean isVerifyingClient, SSLEngine sslEngine) throws CertificateException {
        if (mode == AuthorizationMode.DISABLE) return;

        log.fine(() -> "Verifying certificate: " + createInfoString(certificate, authType, isVerifyingClient));
        AuthorizationResult result = authorizer.authorizePeer(certificate);
        if (sslEngine != null) { // getHandshakeSession() will never return null in this context
            sslEngine.getHandshakeSession().putValue(HANDSHAKE_SESSION_AUTHZ_RESULT_PROPERTY, result);
        }
        if (result.succeeded()) {
            log.fine(() -> String.format("Verification result: %s", result));
        } else {
            String errorMessage = "Authorization failed: " + createInfoString(certificate, authType, isVerifyingClient);
            log.warning(errorMessage);
            if (mode == AuthorizationMode.ENFORCE) {
                throw new CertificateException(errorMessage);
            }
        }
    }

    private static String createInfoString(X509Certificate certificate, String authType, boolean isVerifyingClient) {
        return String.format("DN='%s', SANs=%s, authType='%s', isVerifyingClient='%b'",
                             certificate.getSubjectX500Principal(), X509CertificateUtils.getSubjectAlternativeNames(certificate), authType, isVerifyingClient);
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
