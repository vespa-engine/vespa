// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
import com.yahoo.vespa.jdk8compat.List;

import javax.net.ssl.SSLContext;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Provider for Vespa {@link SSLContext} instance to Zookeeper + misc utility methods for providing Vespa TLS specific ZK configuration.
 *
 * @author bjorncs
 */
public class VespaSslContextProvider implements Supplier<SSLContext> {

    private static final TlsContext tlsContext = TransportSecurityUtils.getSystemTlsContext().orElse(null);

    @Override
    public SSLContext get() {
        if (!tlsEnabled()) throw new IllegalStateException("Vespa TLS is not enabled");
        return tlsContext.context();
    }

    public static boolean tlsEnabled() { return tlsContext != null; }

    public static String enabledTlsProtocolConfigValue() {
        // Fallback to all allowed protocols if we cannot determine which are actually supported by runtime
        Collection<String> enabledProtocols = tlsEnabled() ? List.of(tlsContext.parameters().getProtocols()) : TlsContext.ALLOWED_PROTOCOLS;
        return enabledProtocols.stream().sorted().collect(Collectors.joining(","));
    }

    public static String enabledTlsCiphersConfigValue() {
        // Fallback to all allowed ciphers if we cannot determine which are actually supported by runtime
        Collection<String> enabledCiphers = tlsEnabled() ? List.of(tlsContext.parameters().getCipherSuites()) : TlsContext.ALLOWED_CIPHER_SUITES;
        return enabledCiphers.stream().sorted().collect(Collectors.joining(","));
    }

    public static String sslContextVersion() { return tlsEnabled() ? tlsContext.context().getProtocol() : TlsContext.SSL_CONTEXT_VERSION; }
}
