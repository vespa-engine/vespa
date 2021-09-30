// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl.impl;

import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import java.util.Arrays;
import java.util.List;

/**
 * @author bjorncs
 */
class SslContextFactoryUtils {

    static void setEnabledCipherSuites(SslContextFactory factory, SSLContext sslContext, List<String> enabledCiphers) {
        String[] supportedCiphers = sslContext.getSupportedSSLParameters().getCipherSuites();
        factory.setIncludeCipherSuites(enabledCiphers.toArray(String[]::new));
        factory.setExcludeCipherSuites(createExclusionList(enabledCiphers, supportedCiphers));
    }

    static void setEnabledProtocols(SslContextFactory factory, SSLContext sslContext, List<String> enabledProtocols) {
        String[] supportedProtocols = sslContext.getSupportedSSLParameters().getProtocols();
        factory.setIncludeProtocols(enabledProtocols.toArray(String[]::new));
        factory.setExcludeProtocols(createExclusionList(enabledProtocols, supportedProtocols));
    }

    private static String[] createExclusionList(List<String> enabledValues, String[] supportedValues) {
        return Arrays.stream(supportedValues)
                .filter(supportedValue -> !enabledValues.contains(supportedValue))
                .toArray(String[]::new);
    }
}
