// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * A {@link X509ExtendedTrustManager} which can be updated with new CA certificates while in use.
 *
 * @author bjorncs
 */
public class MutableX509TrustManager extends X509ExtendedTrustManager {

    private volatile X509ExtendedTrustManager currentManager;

    public MutableX509TrustManager(KeyStore truststore) {
        this.currentManager = TrustManagerUtils.createDefaultX509TrustManager(truststore);
    }

    public MutableX509TrustManager() {
        this.currentManager = TrustManagerUtils.createDefaultX509TrustManager();
    }

    public void updateTruststore(KeyStore truststore) {
        this.currentManager = TrustManagerUtils.createDefaultX509TrustManager(truststore);
    }

    public void useDefaultTruststore() {
        this.currentManager = TrustManagerUtils.createDefaultX509TrustManager();
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        currentManager.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        currentManager.checkServerTrusted(chain, authType);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        currentManager.checkClientTrusted(chain, authType, socket);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        currentManager.checkServerTrusted(chain, authType, socket);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        currentManager.checkClientTrusted(chain, authType, sslEngine);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        currentManager.checkServerTrusted(chain, authType, sslEngine);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return currentManager.getAcceptedIssuers();
    }
}
