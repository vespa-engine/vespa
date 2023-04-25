// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author mortent
 */
public class AthenzIdentityProviderProvider implements Provider<AthenzIdentityProvider> {

    private static final ThrowingAthenzIdentityProvider instance = new ThrowingAthenzIdentityProvider();

    @Override
    public AthenzIdentityProvider get() {
        return instance;
    }

    @Override
    public void deconstruct() {
    }

    private static final class ThrowingAthenzIdentityProvider implements AthenzIdentityProvider {

        private static final String message = "AthenzIdentityProvider not available";

        @Override
        public String domain() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public String service() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public SSLContext getIdentitySslContext() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public SSLContext getRoleSslContext(String domain, String role) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public String getRoleToken(String domain) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public String getRoleToken(String domain, String role) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public String getAccessToken(String domain) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public String getAccessToken(String domain, List<String> roles) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public List<X509Certificate> getIdentityCertificate() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public X509Certificate getRoleCertificate(String domain, String role) {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public PrivateKey getPrivateKey() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public Path trustStorePath() {
            throw new UnsupportedOperationException(message);
        }

        @Override
        public void deconstruct() {}
    }

}
