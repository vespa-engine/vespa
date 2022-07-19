// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.WeakHashMap;

/**
 * A {@link X509ExtendedKeyManager} which can be updated with new certificate chain and private key while in use.
 *
 * The implementations assumes that aliases are retrieved from the same thread as the certificate chain and private key.
 * This is case for OpenJDK 11.
 *
 * @author bjorncs
 */
public class MutableX509KeyManager extends X509ExtendedKeyManager {

    private final Object monitor = new Object();
    // Not using ThreadLocal as we want the thread local x509 key manager instances to be garbage collected
    // when either the thread dies or the MutableX509KeyManager instance is collected (latter not the case for ThreadLocal).
    private final WeakHashMap<Thread, X509ExtendedKeyManager> threadLocalManager = new WeakHashMap<>();
    private X509ExtendedKeyManager currentManager;

    public MutableX509KeyManager(KeyStore keystore, char[] password) {
        synchronized (monitor) {
            this.currentManager = KeyManagerUtils.createDefaultX509KeyManager(keystore, password);
        }
    }

    public MutableX509KeyManager() {
        synchronized (monitor) {
            this.currentManager = KeyManagerUtils.createDefaultX509KeyManager();
        }
    }

    public void updateKeystore(KeyStore keystore, char[] password) {
        synchronized (monitor) {
            this.currentManager = KeyManagerUtils.createDefaultX509KeyManager(keystore, password);
        }
    }

    public void useDefaultKeystore() {
        synchronized (monitor) {
            this.currentManager = KeyManagerUtils.createDefaultX509KeyManager();
        }
    }

    public X509ExtendedKeyManager currentManager() {
        synchronized (monitor) {
            return currentManager;
        }
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return updateAndGetThreadLocalManager()
                .getServerAliases(keyType, issuers);
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return updateAndGetThreadLocalManager()
                .getClientAliases(keyType, issuers);
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return updateAndGetThreadLocalManager()
                .chooseServerAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        return updateAndGetThreadLocalManager()
                .chooseClientAlias(keyType, issuers, socket);
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        return updateAndGetThreadLocalManager()
                .chooseEngineServerAlias(keyType, issuers, engine);
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
        return updateAndGetThreadLocalManager()
                .chooseEngineClientAlias(keyType, issuers, engine);
    }

    private X509ExtendedKeyManager updateAndGetThreadLocalManager() {
        synchronized (monitor) {
            X509ExtendedKeyManager currentManager = this.currentManager;
            threadLocalManager.put(Thread.currentThread(), currentManager);
            return currentManager;
        }
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        if (alias == null) return null; // this method can be called with 'null' alias prior to any alias getter methods.
        return getThreadLocalManager()
                .getCertificateChain(alias);
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        if (alias == null) return null; // this method can be called with 'null' alias prior to any alias getter methods.
        return getThreadLocalManager()
                .getPrivateKey(alias);
    }

    private X509ExtendedKeyManager getThreadLocalManager() {
        synchronized (monitor) {
            X509ExtendedKeyManager manager = threadLocalManager.get(Thread.currentThread());
            if (manager == null) {
                throw new IllegalStateException("Methods to retrieve valid aliases has not been called previously from this thread");
            }
            return manager;
        }
    }
}
