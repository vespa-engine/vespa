// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.pem;

import com.yahoo.jdisc.http.ssl.SslKeyStore;
import com.yahoo.jdisc.http.ssl.pem.PemKeyStore.KeyStoreLoadParameter;
import com.yahoo.jdisc.http.ssl.pem.PemKeyStore.TrustStoreLoadParameter;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStore.LoadStoreParameter;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;

/**
 * Responsible for creating pem key stores.
 *
 * @author Tony Vaagenes
 * @author bjorncs
 */
public class PemSslKeyStore implements SslKeyStore {

    static {
        Security.addProvider(new PemKeyStoreProvider());
    }

    private static final String KEY_STORE_TYPE = "PEM";

    private final LoadStoreParameter loadParameter;
    private KeyStore keyStore;

    public PemSslKeyStore(Path certificatePath, Path keyPath) {
        this.loadParameter = new KeyStoreLoadParameter(certificatePath, keyPath);
    }

    public PemSslKeyStore(Path certificatePath) {
        this.loadParameter = new TrustStoreLoadParameter(certificatePath);
    }

    @Override
    public KeyStore loadJavaKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        //cached since Reader(in loadParameter) can only be used one time.
        if (keyStore == null) {
            keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
            keyStore.load(loadParameter);
        }
        return keyStore;
    }

    private static class PemKeyStoreProvider extends Provider {

        static final String NAME = "PEMKeyStoreProvider";
        static final double VERSION = 1;
        static final String DESCRIPTION = "Provides PEM keystore support";

        PemKeyStoreProvider() {
            super(NAME, VERSION, DESCRIPTION);
            putService(new Service(this, "KeyStore", "PEM", PemKeyStore. class.getName(), PemKeyStore.aliases, PemKeyStore.attributes));
        }
    }

}
