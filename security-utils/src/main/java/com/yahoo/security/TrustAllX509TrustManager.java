// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.security.cert.X509Certificate;

/**
 * A {@link X509ExtendedTrustManager} that accepts all server certificates.
 *
 * @author bjorncs
 */
public class TrustAllX509TrustManager extends X509ExtendedTrustManager {
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) { failWhenUsedOnServer(); }
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { failWhenUsedOnServer(); }
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { failWhenUsedOnServer(); }

    @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {}
    @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {}
    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }

    private static void failWhenUsedOnServer() {
        throw new IllegalStateException("TrustAllX509TrustManager cannot be used on server, only client");
    }
}
