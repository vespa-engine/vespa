// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.test;

import com.yahoo.jdisc.http.server.jetty.JettyHttpServer;
import com.yahoo.jdisc.http.ssl.SslContextFactory;
import com.yahoo.jdisc.http.ssl.SslKeyStore;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class RemoteClient extends ChunkReader {

    private final Socket socket;

    private RemoteClient(Socket socket) throws IOException {
        super(socket.getInputStream());
        this.socket = socket;
    }

    public void close() throws IOException {
        socket.close();
    }

    public void writeRequest(String request) throws IOException {
        socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
    }

    public static RemoteClient newInstance(JettyHttpServer server) throws IOException {
        return newInstance(server.getListenPort());
    }

    public static RemoteClient newInstance(int listenPort) throws IOException {
        return new RemoteClient(new Socket("localhost", listenPort));
    }

    public static RemoteClient newSslInstance(int listenPort, SslKeyStore sslKeyStore) throws IOException {
        SSLContext ctx = SslContextFactory.newInstanceFromTrustStore(sslKeyStore).getServerSSLContext();
        if (ctx == null) {
            throw new RuntimeException("Failed to create socket with SSLContext.");
        }
        return new RemoteClient(ctx.getSocketFactory().createSocket("localhost", listenPort));
    }

    public static RemoteClient newSslInstance(JettyHttpServer server, SslKeyStore keyStore) throws IOException {
        return newSslInstance(server.getListenPort(), keyStore);
    }

}
