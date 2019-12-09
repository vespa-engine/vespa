// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.CertificateUtils;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.security.KeyStore;
import java.util.Objects;

/**
 * A modified {@link SslContextFactory} that allows passwordless truststore in combination with password protected keystore.
 *
 * @author bjorncs
 */
class JDiscSslContextFactory extends SslContextFactory.Server {

    private String trustStorePassword;

    @Override
    public void setTrustStorePassword(String password) {
        super.setTrustStorePassword(password);
        this.trustStorePassword = password;
    }


    // Overriden to stop Jetty from using the keystore password if no truststore password is specified.
    @Override
    protected KeyStore loadTrustStore(Resource resource) throws Exception {
        return CertificateUtils.getKeyStore(
                resource != null ? resource : getKeyStoreResource(),
                Objects.toString(getTrustStoreType(), getKeyStoreType()),
                Objects.toString(getTrustStoreProvider(), getKeyStoreProvider()),
                trustStorePassword);
    }
}
