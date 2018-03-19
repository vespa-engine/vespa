// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;

/**
 * @author bjorncs
 */
public enum KeyStoreType {
    JKS {
        KeyStore createKeystore() throws KeyStoreException {
            return KeyStore.getInstance("JKS");
        }
    },
    PKCS12 {
        KeyStore createKeystore() throws KeyStoreException {
            return KeyStore.getInstance("PKCS12", BouncyCastleProviderHolder.getInstance());
        }
    };
    abstract KeyStore createKeystore() throws GeneralSecurityException;
}
