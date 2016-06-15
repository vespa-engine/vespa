// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Optional;

/**
 *
 * @author <a href="mailto:charlesk@yahoo-inc.com">Charles Kim</a>
 */
public abstract class SslKeyStore {

	private Optional<String> keyStorePassword = Optional.empty();

    public Optional<String> getKeyStorePassword() {
		return keyStorePassword;
	}

	public void setKeyStorePassword(String keyStorePassword) {
		this.keyStorePassword = Optional.of(keyStorePassword);
	}

    public abstract KeyStore loadJavaKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException;

}
