// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.socket.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Simon Thoresen Hult
 */
public class SocketTestApp {

    private final static byte[] REQUEST = "foo".getBytes(StandardCharsets.UTF_8);
    private final static byte[] RESPONSE = "bar".getBytes(StandardCharsets.UTF_8);
    private static volatile int numRequests = 0;

    public static void main(String[] args) throws IOException {
        final boolean soLingerOn = Boolean.valueOf(args[0]);
        final int soLingerTime = Integer.valueOf(args[1]);
        final int numClients = Integer.valueOf(args[2]);
        final int sleepMillis = Integer.valueOf(args[3]);

        System.out.println("soLingerOn = " + soLingerOn);
        System.out.println("soLingerTime = " + soLingerTime);
        System.out.println("numClients = " + numClients);
        System.out.println("sleepMillis = " + sleepMillis);

        ServerSocket serverSocket = new ServerSocket(0);
        for (int i = 0; i < numClients; ++i) {
            new Client(serverSocket.getLocalPort()).start();
        }

        Executor workers = Executors.newFixedThreadPool(numClients);
        long prev = System.currentTimeMillis();
        while (true) {
            long next = System.currentTimeMillis();
            if (next > prev + 1000) {
                System.err.println((numRequests * 1000) / (next - prev));
                numRequests = 0;
                prev = next;
            }
            final Socket clientSocket = serverSocket.accept();
            clientSocket.setSoLinger(soLingerOn, soLingerTime);
            workers.execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        InputStream in = clientSocket.getInputStream();
                        for (byte expected : REQUEST) {
                            int actual = in.read();
                            if (actual != expected) {
                                throw new AssertionError("Expected '" + expected + "', got '" + actual + "'.");
                            }
                        }
                        Thread.sleep(sleepMillis);
                        OutputStream out = clientSocket.getOutputStream();
                        out.write(RESPONSE);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    private static class Client extends Thread {

        final int port;

        Client(int port) throws IOException {
            this.port = port;
        }

        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    Socket socket = new Socket("localhost", port);
                    OutputStream out = socket.getOutputStream();
                    out.write(REQUEST);

                    InputStream in = socket.getInputStream();
                    for (byte expected : RESPONSE) {
                        int actual = in.read();
                        if (actual != expected) {
                            throw new AssertionError("Expected '" + expected + "', got '" + actual + "'.");
                        }
                    }
                    socket.close();
                    ++numRequests;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
