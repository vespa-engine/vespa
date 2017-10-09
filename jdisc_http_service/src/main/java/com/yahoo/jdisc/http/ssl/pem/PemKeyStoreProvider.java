// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.pem;

import java.security.Provider;

/**
 * @author Tony Vaagenes
 */
public class PemKeyStoreProvider extends Provider {

    public static final String name = "PEMKeyStoreProvider";
    public static final double version = 1;
    public static final String description = "Provides PEM keystore support";

    public PemKeyStoreProvider() {
        super(name, version, description);
        putService(new Service(this, "KeyStore", "PEM", PemKeyStore. class.getName(), PemKeyStore.aliases, PemKeyStore.attributes));
    }

}
