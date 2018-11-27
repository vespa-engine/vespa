// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.authz;

import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.tls.policy.AuthorizedPeers;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Logger;

/**
 * A {@link X509ExtendedTrustManager} that performs additional certificate verification through {@link PeerAuthorizer}.
 *
 * @author bjorncs
 */
// TODO Propagate verification results
// Note: Implementation assumes that provided X509ExtendedTrustManager will throw IllegalArgumentException when chain is empty or null
public class PeerAuthorizerTrustManager extends X509ExtendedTrustManager {

    private static final Logger log = Logger.getLogger(PeerAuthorizerTrustManager.class.getName());

    public enum Mode { DRY_RUN, ENFORCE }

    private final PeerAuthorizer authorizer;
    private final X509ExtendedTrustManager defaultTrustManager;
    private final Mode mode;

    public PeerAuthorizerTrustManager(AuthorizedPeers authorizedPeers, Mode mode, X509ExtendedTrustManager defaultTrustManager) {
        this.authorizer = new PeerAuthorizer(authorizedPeers);
        this.mode = mode;
        this.defaultTrustManager = defaultTrustManager;
    }

    public static TrustManager[] wrapTrustManagersFromKeystore(AuthorizedPeers authorizedPeers, Mode mode, KeyStore keystore) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keystore);
        return wrapTrustManagers(authorizedPeers, mode, factory.getTrustManagers());
    }

    public static TrustManager[] wrapTrustManagers(AuthorizedPeers authorizedPeers, Mode mode, TrustManager[] managers) {
        TrustManager[] wrappedManagers = new TrustManager[managers.length];
        for (int i = 0; i < managers.length; i++) {
            if (managers[i] instanceof X509ExtendedTrustManager) {
                wrappedManagers[i] = new PeerAuthorizerTrustManager(authorizedPeers, mode, (X509ExtendedTrustManager) managers[i]);
            } else {
                wrappedManagers[i] = managers[i];
            }
        }
        return wrappedManagers;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType);
        authorizePeer(chain[0], authType, true);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        defaultTrustManager.checkServerTrusted(chain, authType);
        authorizePeer(chain[0], authType, false);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType, socket);
        authorizePeer(chain[0], authType, true);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        defaultTrustManager.checkServerTrusted(chain, authType, socket);
        authorizePeer(chain[0], authType, false);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain, authType, sslEngine);
        authorizePeer(chain[0], authType, true);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        defaultTrustManager.checkServerTrusted(chain, authType, sslEngine);
        authorizePeer(chain[0], authType, false);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager.getAcceptedIssuers();
    }

    private void authorizePeer(X509Certificate certificate, String authType, boolean isVerifyingClient) throws CertificateException {
        log.fine(() -> "Verifying certificate: " + createInfoString(certificate, authType, isVerifyingClient));
        AuthorizationResult result = authorizer.authorizePeer(certificate);
        if (result.succeeded()) {
            log.fine(() -> String.format("Verification result: %s", result));
        } else {
            String errorMessage = "Authorization failed: " + createInfoString(certificate, authType, isVerifyingClient);
            switch (mode) {
                case ENFORCE:
                    throw new CertificateException(errorMessage);
                case DRY_RUN:
                    log.warning(errorMessage);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    private static String createInfoString(X509Certificate certificate, String authType, boolean isVerifyingClient) {
        return String.format("DN='%s', SANs=%s, authType='%s', isVerifyingClient='%b'",
                             certificate.getSubjectX500Principal(), X509CertificateUtils.getSubjectAlternativeNames(certificate), authType, isVerifyingClient);
    }

}
