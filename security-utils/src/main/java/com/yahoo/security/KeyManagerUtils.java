// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * Utility methods for constructing {@link X509ExtendedKeyManager}.
 *
 * @author bjorncs
 */
public class KeyManagerUtils {

    public static X509ExtendedKeyManager createDefaultX509KeyManager(KeyStore keystore, char[] password) {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, password);
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            return Arrays.stream(keyManagers)
                    .filter(manager -> manager instanceof X509ExtendedKeyManager)
                    .map(X509ExtendedKeyManager.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No X509ExtendedKeyManager in " + Arrays.asList(keyManagers)));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static X509ExtendedKeyManager createDefaultX509KeyManager(PrivateKey privateKey, List<X509Certificate> certificateChain) {
        KeyStore keystore = KeyStoreBuilder.withType(KeyStoreType.PKCS12)
                .withKeyEntry("default", privateKey, certificateChain)
                .build();
        return createDefaultX509KeyManager(keystore, new char[0]);
    }

    public static X509ExtendedKeyManager createDefaultX509KeyManager() {
        return createDefaultX509KeyManager(null, new char[0]);
    }
}
