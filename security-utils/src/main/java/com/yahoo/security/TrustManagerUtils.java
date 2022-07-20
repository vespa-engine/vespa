// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import com.yahoo.security.KeyStoreBuilder;
import com.yahoo.security.KeyStoreType;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * Utility methods for constructing {@link X509ExtendedTrustManager}.
 *
 * @author bjorncs
 */
public class TrustManagerUtils {

    public static X509ExtendedTrustManager createDefaultX509TrustManager(KeyStore truststore) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(truststore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            return Arrays.stream(trustManagers)
                    .filter(manager -> manager instanceof X509ExtendedTrustManager)
                    .map(X509ExtendedTrustManager.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No X509ExtendedTrustManager in " + Arrays.asList(trustManagers)));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static X509ExtendedTrustManager createDefaultX509TrustManager(List<X509Certificate> certificates) {
        return createDefaultX509TrustManager(
                KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                        .withCertificateEntries("cert", certificates)
                        .build());
    }

    public static X509ExtendedTrustManager createDefaultX509TrustManager() {
        return createDefaultX509TrustManager((KeyStore) null);
    }
}
