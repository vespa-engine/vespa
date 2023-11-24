/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.common;

import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.util.Arrays;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;

import org.apache.zookeeper.common.X509Exception.KeyManagerException;
import org.apache.zookeeper.common.X509Exception.SSLContextException;
import org.apache.zookeeper.server.auth.ProviderRegistry;
import org.apache.zookeeper.server.auth.X509AuthenticationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * <em>NOTE: Overridden because ZK 3.9 completely broke the SSL setup APIs; for clients, key and trust stores are
 * now mandatory, unlike for servers, where it's still possible to provide a custom authProvider. This patch fixes that.
 * Based on https://github.com/apache/zookeeper/blob/branch-3.9/zookeeper-server/src/main/java/org/apache/zookeeper/common/ClientX509Util.java</em>
 * <p>
 * X509 utilities specific for client-server communication framework.
 */
public class ClientX509Util extends X509Util {

    private static final Logger LOG = LoggerFactory.getLogger(ClientX509Util.class);

    private final String sslAuthProviderProperty = getConfigPrefix() + "authProvider";
    private final String sslProviderProperty = getConfigPrefix() + "sslProvider";

    @Override
    protected String getConfigPrefix() {
        return "zookeeper.ssl.";
    }

    @Override
    protected boolean shouldVerifyClientHostname() {
        return false;
    }

    public String getSslAuthProviderProperty() {
        return sslAuthProviderProperty;
    }

    public String getSslProviderProperty() {
        return sslProviderProperty;
    }

    public SslContext createNettySslContextForClient(ZKConfig config)
        throws X509Exception.KeyManagerException, X509Exception.TrustManagerException, SSLException {

        KeyManager km;
        TrustManager tm;
        String authProviderProp = System.getProperty(getSslAuthProviderProperty());
        if (authProviderProp == null) {
            String keyStoreLocation = config.getProperty(getSslKeystoreLocationProperty(), "");
            String keyStorePassword = getPasswordFromConfigPropertyOrFile(config, getSslKeystorePasswdProperty(),
                                                                          getSslKeystorePasswdPathProperty());
            String keyStoreType = config.getProperty(getSslKeystoreTypeProperty());
            if (keyStoreLocation.isEmpty()) {
                LOG.warn("{} not specified", getSslKeystoreLocationProperty());
                km = null;
            } else {
                km = createKeyManager(keyStoreLocation, keyStorePassword, keyStoreType);
            }
            tm = getTrustManager(config);
        } else {
            X509AuthenticationProvider authProvider = (X509AuthenticationProvider) ProviderRegistry.getProvider(
                    System.getProperty(getSslAuthProviderProperty(), "x509"));

            if (authProvider == null) {
                LOG.error("Auth provider not found: {}", authProviderProp);
                throw new SSLException("Could not create SSLContext with specified auth provider: " + authProviderProp);
            }
            LOG.info("Using auth provider for client: {}", authProviderProp);
            km = authProvider.getKeyManager();
            tm = authProvider.getTrustManager();
        }

        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();
        if (km != null) {
            sslContextBuilder.keyManager(km);
        }
        if (tm != null) {
            sslContextBuilder.trustManager(tm);
        }

        return createNettySslContext(config, sslContextBuilder, "Server");
    }

    public SslContext createNettySslContextForServer(ZKConfig config)
        throws X509Exception.SSLContextException, X509Exception.KeyManagerException, X509Exception.TrustManagerException, SSLException {
        String keyStoreLocation = config.getProperty(getSslKeystoreLocationProperty(), "");
        String keyStorePassword = getPasswordFromConfigPropertyOrFile(config, getSslKeystorePasswdProperty(),
            getSslKeystorePasswdPathProperty());
        String keyStoreType = config.getProperty(getSslKeystoreTypeProperty());

        if (keyStoreLocation.isEmpty()) {
            throw new X509Exception.SSLContextException(
                "Keystore is required for SSL server: " + getSslKeystoreLocationProperty());
        }

        KeyManager km = createKeyManager(keyStoreLocation, keyStorePassword, keyStoreType);
        TrustManager trustManager = getTrustManager(config);

        return createNettySslContextForServer(config, km, trustManager);
    }

    public SslContext createNettySslContextForServer(ZKConfig config, KeyManager km, TrustManager tm) throws SSLException {
        SslContextBuilder sslContextBuilder = SslContextBuilder.forServer(km);
        if (tm != null) {
            sslContextBuilder.trustManager(tm);
        }
        return createNettySslContext(config, sslContextBuilder, "Client");
    }

    SslContext createNettySslContext(ZKConfig config, SslContextBuilder sslContextBuilder, String clientOrServer) throws SSLException {
        sslContextBuilder.enableOcsp(config.getBoolean(getSslOcspEnabledProperty()));
        sslContextBuilder.protocols(getEnabledProtocols(config));
        sslContextBuilder.clientAuth(getClientAuth(config).toNettyClientAuth());
        Iterable<String> enabledCiphers = getCipherSuites(config);
        if (enabledCiphers != null) {
            sslContextBuilder.ciphers(enabledCiphers);
        }
        sslContextBuilder.sslProvider(getSslProvider(config));

        SslContext sslContext = sslContextBuilder.build();

        if (getFipsMode(config) && isClientHostnameVerificationEnabled(config)) {
            return addHostnameVerification(sslContext, clientOrServer);
        } else {
            return sslContext;
        }
    }

    private SslContext addHostnameVerification(SslContext sslContext, String clientOrServer) {
        return new DelegatingSslContext(sslContext) {
            @Override
            protected void initEngine(SSLEngine sslEngine) {
                SSLParameters sslParameters = sslEngine.getSSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslEngine.setSSLParameters(sslParameters);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} hostname verification: enabled HTTPS style endpoint identification algorithm", clientOrServer);
                }
            }
        };
    }

    private String[] getEnabledProtocols(final ZKConfig config) {
        String enabledProtocolsInput = config.getProperty(getSslEnabledProtocolsProperty());
        if (enabledProtocolsInput == null) {
            return new String[]{ config.getProperty(getSslProtocolProperty(), DEFAULT_PROTOCOL) };
        }
        return enabledProtocolsInput.split(",");
    }

    private X509Util.ClientAuth getClientAuth(final ZKConfig config) {
        return X509Util.ClientAuth.fromPropertyValue(config.getProperty(getSslClientAuthProperty()));
    }

    private Iterable<String> getCipherSuites(final ZKConfig config) {
        String cipherSuitesInput = config.getProperty(getSslCipherSuitesProperty());
        if (cipherSuitesInput == null) {
            if (getSslProvider(config) != SslProvider.JDK) {
                return null;
            }
            return Arrays.asList(X509Util.getDefaultCipherSuites());
        } else {
            return Arrays.asList(cipherSuitesInput.split(","));
        }
    }

    public SslProvider getSslProvider(ZKConfig config) {
        return SslProvider.valueOf(config.getProperty(getSslProviderProperty(), "JDK"));
    }

    private TrustManager getTrustManager(ZKConfig config) throws X509Exception.TrustManagerException {
        String trustStoreLocation = config.getProperty(getSslTruststoreLocationProperty(), "");
        String trustStorePassword = getPasswordFromConfigPropertyOrFile(config, getSslTruststorePasswdProperty(),
            getSslTruststorePasswdPathProperty());
        String trustStoreType = config.getProperty(getSslTruststoreTypeProperty());

        boolean sslCrlEnabled = config.getBoolean(getSslCrlEnabledProperty());
        boolean sslOcspEnabled = config.getBoolean(getSslOcspEnabledProperty());
        boolean sslServerHostnameVerificationEnabled = isServerHostnameVerificationEnabled(config);
        boolean sslClientHostnameVerificationEnabled = isClientHostnameVerificationEnabled(config);

        if (trustStoreLocation.isEmpty()) {
            LOG.warn("{} not specified", getSslTruststoreLocationProperty());
            return null;
        } else {
            return createTrustManager(trustStoreLocation, trustStorePassword, trustStoreType,
                sslCrlEnabled, sslOcspEnabled, sslServerHostnameVerificationEnabled,
                sslClientHostnameVerificationEnabled, getFipsMode(config));
        }
    }
}
