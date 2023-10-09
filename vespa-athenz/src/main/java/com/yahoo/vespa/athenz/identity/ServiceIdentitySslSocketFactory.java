// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.function.Supplier;

/**
 * A {@link SSLSocketFactory} that creates sockets through the {@link SSLContext} provided by {@link ServiceIdentityProvider}.
 *
 * Note: the implementation assumes that the set of default/supported cipher suites is static for a given {@link ServiceIdentityProvider} instance.
 *
 * @author bjorncs
 */
public class ServiceIdentitySslSocketFactory extends SSLSocketFactory {

    private final Object monitor = new Object();
    private final Supplier<SSLContext> sslContextSupplier;
    private final String[] defaultCipherSuites;
    private final String[] supportedCipherSuites;
    private SSLContext sslContext;
    private SSLSocketFactory sslSocketFactory;

    public ServiceIdentitySslSocketFactory(ServiceIdentityProvider serviceIdentityProvider) {
        this(serviceIdentityProvider::getIdentitySslContext);
    }

    public ServiceIdentitySslSocketFactory(Supplier<SSLContext> sslContextSupplier) {
        super();
        SSLContext sslContext = sslContextSupplier.get();
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        this.sslContextSupplier = sslContextSupplier;
        this.defaultCipherSuites = sslSocketFactory.getDefaultCipherSuites();
        this.supportedCipherSuites = sslSocketFactory.getSupportedCipherSuites();
        this.sslContext = sslContext;
        this.sslSocketFactory = sslSocketFactory;
    }

    private SSLSocketFactory currentSslSocketFactory() {
        SSLContext currentSslContext = sslContextSupplier.get();
        synchronized (monitor) {
            if (currentSslContext != sslContext) {
                sslContext = currentSslContext;
                sslSocketFactory = currentSslContext.getSocketFactory();
            }
            return sslSocketFactory;
        }
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return defaultCipherSuites;
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return supportedCipherSuites;
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return currentSslSocketFactory().createSocket(s, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return currentSslSocketFactory().createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return currentSslSocketFactory().createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return currentSslSocketFactory().createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return currentSslSocketFactory().createSocket(address, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(Socket s, InputStream consumed, boolean autoClose) throws IOException {
        return currentSslSocketFactory().createSocket(s, consumed, autoClose);
    }

    @Override
    public Socket createSocket() throws IOException {
        return currentSslSocketFactory().createSocket();
    }

}
