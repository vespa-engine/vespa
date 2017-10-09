// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.pem;

import com.yahoo.jdisc.http.ssl.SslKeyStore;
import com.yahoo.jdisc.http.ssl.pem.PemKeyStore.KeyStoreLoadParameter;
import com.yahoo.jdisc.http.ssl.pem.PemKeyStore.PemLoadStoreParameter;
import com.yahoo.jdisc.http.ssl.pem.PemKeyStore.TrustStoreLoadParameter;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;

/**
 * Responsible for creating pem key stores.
 *
 * @author Tony Vaagenes
 */
public class PemSslKeyStore extends SslKeyStore {

    static {
        Security.addProvider(new PemKeyStoreProvider());
    }

    private static final String keyStoreType = "PEM";
    private final PemLoadStoreParameter loadParameter;
    private KeyStore keyStore;

    public PemSslKeyStore(KeyStoreLoadParameter loadParameter) {
        this.loadParameter = loadParameter;
    }

    public PemSslKeyStore(TrustStoreLoadParameter loadParameter) {
        this.loadParameter = loadParameter;
    }

    @Override
    public KeyStore loadJavaKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        if (getKeyStorePassword().isPresent()) {
            throw new UnsupportedOperationException("PEM key store with password is currently not supported. Please file a feature request.");
        }

        //cached since Reader(in loadParameter) can only be used one time.
        if (keyStore == null) {
            keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(loadParameter);
        }
        return keyStore;
    }

}
