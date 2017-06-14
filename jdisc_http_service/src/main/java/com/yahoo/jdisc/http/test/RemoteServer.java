// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.test;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class RemoteServer implements Runnable {

    private final Thread thread = new Thread(this, "RemoteServer@" + System.identityHashCode(this));
    private final LinkedBlockingQueue<Socket> clients = new LinkedBlockingQueue<>();
    private final ServerSocket server;

    private RemoteServer(int listenPort) throws IOException {
        this.server = new ServerSocket(listenPort);
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                Socket client = server.accept();
                if (client != null) {
                    clients.add(client);
                }
            }
        } catch (IOException e) {
            if (!server.isClosed()) {
                e.printStackTrace();
            }
        }
    }

    public URI newRequestUri(String uri) {
        return newRequestUri(URI.create(uri));
    }

    public URI newRequestUri(URI uri) {
        URI serverUri = connectionSpec();
        try {
            return new URI(serverUri.getScheme(), serverUri.getUserInfo(), serverUri.getHost(),
                           serverUri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public URI connectionSpec() {
        return URI.create("http://localhost:" + server.getLocalPort() + "/");
    }

    public Connection awaitConnection(int timeout, TimeUnit unit) throws InterruptedException, IOException {
        Socket client = clients.poll(timeout, unit);
        if (client == null) {
            return null;
        }
        return new Connection(client);
    }

    public boolean close(int timeout, TimeUnit unit) {
        try {
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        try {
            thread.join(unit.toMillis(timeout));
        } catch (InterruptedException e) {
            return false;
        }
        return !thread.isAlive();
    }

    public static RemoteServer newInstance() throws IOException {
        RemoteServer ret = new RemoteServer(0);
        ret.thread.start();
        return ret;
    }

    public static class Connection extends ChunkReader {

        private final Socket socket;
        private final PrintWriter out;

        private Connection(Socket socket) throws IOException {
            super(socket.getInputStream());
            this.socket = socket;
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        }

        public void writeChunk(String chunk) {
            out.print(chunk);
        }

        public void close() throws IOException {
            out.close();
            socket.close();
        }
    }
}
