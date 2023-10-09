// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.http.CertificateStore;

/**
 * An certificate store provider which provides a factory which throws exception on
 * invocation - as no certificate store is currently provided by default.
 * The purpose of this is to provide a certificate store for injection in the case where
 * no certificate store component is provided.
 *
 * @author bratseth
 */
@SuppressWarnings("unused")
public class CertificateStoreProvider implements Provider<CertificateStore> {

    private static final ThrowingCertificateStore instance = new ThrowingCertificateStore();

    @Override
    public CertificateStore get() { return instance; }

    @Override
    public void deconstruct() { }

    private static final class ThrowingCertificateStore implements CertificateStore {

        @Override
        public String getCertificate(String key, long ttl, long retry) {
            throw new UnsupportedOperationException("A certificate store is not available");
        }

    }

}
