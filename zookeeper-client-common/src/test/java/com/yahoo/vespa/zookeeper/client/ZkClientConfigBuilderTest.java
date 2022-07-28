// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper.client;

import com.yahoo.security.tls.TlsContext;
import org.apache.zookeeper.client.ZKClientConfig;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import java.util.List;

import static com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder.CLIENT_CONNECTION_SOCKET;
import static com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder.CLIENT_SECURE_PROPERTY;
import static com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder.SSL_CLIENTAUTH_PROPERTY;
import static com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder.SSL_CONTEXT_SUPPLIER_CLASS_PROPERTY;
import static com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder.SSL_ENABLED_CIPHERSUITES_PROPERTY;
import static com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder.SSL_ENABLED_PROTOCOLS_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the zookeeper client config builder.
 */
public class ZkClientConfigBuilderTest {

    @Test
    void config_when_not_using_tls_context() {
        ZkClientConfigBuilder builder = new ZkClientConfigBuilder(null);
        ZKClientConfig config = builder.toConfig();
        assertEquals("false", config.getProperty(CLIENT_SECURE_PROPERTY));
        assertEquals("org.apache.zookeeper.ClientCnxnSocketNetty", config.getProperty(CLIENT_CONNECTION_SOCKET));
        assertNull(config.getProperty(SSL_CONTEXT_SUPPLIER_CLASS_PROPERTY));
        assertNull(config.getProperty(SSL_CLIENTAUTH_PROPERTY));
    }

    @Test
    void config_when_using_system_tls_context() {
        ZkClientConfigBuilder builder = new ZkClientConfigBuilder(new MockTlsContext());
        ZKClientConfig config = builder.toConfig();
        assertEquals("true", config.getProperty(CLIENT_SECURE_PROPERTY));
        assertEquals("org.apache.zookeeper.ClientCnxnSocketNetty", config.getProperty(CLIENT_CONNECTION_SOCKET));
        assertEquals(com.yahoo.vespa.zookeeper.client.VespaSslContextProvider.class.getName(), config.getProperty(SSL_CONTEXT_SUPPLIER_CLASS_PROPERTY));
        assertEquals("TLSv1.3", config.getProperty(SSL_ENABLED_PROTOCOLS_PROPERTY));
        assertEquals("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", config.getProperty(SSL_ENABLED_CIPHERSUITES_PROPERTY));
        assertEquals("NEED", config.getProperty(SSL_CLIENTAUTH_PROPERTY));
    }

    private static class MockTlsContext implements TlsContext {

        @Override
        public SSLContext context() {
            return null;
        }

        @Override
        public SSLParameters parameters() {
            SSLParameters parameters = new SSLParameters();
            parameters.setProtocols(List.of("TLSv1.3").toArray(new String[0]));
            parameters.setCipherSuites(List.of("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384").toArray(new String[0]));
            parameters.setNeedClientAuth(true);
            return parameters;
        }

        @Override
        public SSLEngine createSslEngine() {
            return null;
        }

        @Override
        public SSLEngine createSslEngine(String peerHost, int peerPort) {
            return null;
        }
    }


}
