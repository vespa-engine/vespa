// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * @author bjorncs
 */
class SslContextFactoryUtils {

    static void setEnabledCipherSuites(SslContextFactory factory, SSLContext sslContextOrNull, List<String> enabledCiphers) {
        String[] supportedCiphers = supportedSslParams(sslContextOrNull).getCipherSuites();
        factory.setIncludeCipherSuites(enabledCiphers.toArray(String[]::new));
        factory.setExcludeCipherSuites(createExclusionList(enabledCiphers, supportedCiphers));
    }

    static void setEnabledProtocols(SslContextFactory factory, SSLContext sslContextOrNull, List<String> enabledProtocols) {
        String[] supportedProtocols = supportedSslParams(sslContextOrNull).getProtocols();
        factory.setIncludeProtocols(enabledProtocols.toArray(String[]::new));
        factory.setExcludeProtocols(createExclusionList(enabledProtocols, supportedProtocols));
    }

    private static String[] createExclusionList(List<String> enabledValues, String[] supportedValues) {
        return Arrays.stream(supportedValues)
                .filter(supportedValue -> !enabledValues.contains(supportedValue))
                .toArray(String[]::new);
    }

    private static SSLParameters supportedSslParams(SSLContext ctx) {
        try {
            return ctx != null
                    ? ctx.getSupportedSSLParameters()
                    : SSLContext.getDefault().getSupportedSSLParameters();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
