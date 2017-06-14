// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search;

import com.yahoo.text.Utf8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * As the name implies, a stupid, single-threaded bad-excuse-for-HTTP server.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class StupidSingleThreadedHttpServer implements Runnable {

    private static final Logger log = Logger.getLogger(StupidSingleThreadedHttpServer.class.getName());

    private final ServerSocket serverSocket;
    private final int delaySeconds;
    private Thread serverThread = null;
    private CompletableFuture<String> requestFuture = new CompletableFuture<>();
    private final Pattern contentLengthPattern = Pattern.compile("content-length: (\\d+)",
                                                                 Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

    public StupidSingleThreadedHttpServer() throws IOException {
        this(0, 0);
    }

    public StupidSingleThreadedHttpServer(int port, int delaySeconds) throws IOException {
        this.delaySeconds = delaySeconds;
        this.serverSocket = new ServerSocket(port);
    }

    public void start() {
        serverThread = new Thread(this);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void run() {
        try {
            while(true) {
                Socket socket = serverSocket.accept();
                StringBuilder request = new StringBuilder();
                socket.setSoLinger(true, 60);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream()));

                int contentLength = -1;
                String inputLine;
                while (!"".equals(inputLine = in.readLine())) {  //read header:
                    request.append(inputLine).append("\r\n");
                    if (inputLine.toLowerCase(Locale.US).contains("content-length")) {
                        Matcher contentLengthMatcher = contentLengthPattern.matcher(inputLine);
                        if (contentLengthMatcher.matches()) {
                            contentLength = Integer.parseInt(contentLengthMatcher.group(1));
                        }
                    }
                }
                request.append("\r\n");

                if (contentLength < 0) {
                    System.err.println("WARNING! Got no Content-Length header!!");
                } else {
                    char[] requestBody = new char[contentLength];
                    int readRemaining = contentLength;

                    do {
                        int read = in.read(requestBody, (contentLength - readRemaining), readRemaining);
                        if (read < 0) {
                            throw new IllegalStateException("Should not get EOF here!!");
                        }
                        readRemaining -= read;
                    } while (readRemaining > 0);

                    request.append(new String(requestBody));
                }

                // Simulate service slowness
                if (delaySeconds > 0) {
                    try {
                        System.out.println(this.getClass().getCanonicalName() + " sleeping in " + delaySeconds + " s before responding...");
                        Thread.sleep((long) (delaySeconds * 1000));
                        System.out.println("done sleeping, responding");
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }

                socket.getOutputStream().write(getResponse(request.toString()));
                socket.getOutputStream().flush();
                in.close();
                socket.close();

                boolean wasCompleted = requestFuture.complete(request.toString());
                if (!wasCompleted) {
                    log.log(Level.INFO, "Only the first request will be stored, ignoring. "
                            + "Old value: " + requestFuture.get()
                            + ", New value: " + request.toString());
                }
            }
        } catch (SocketException se) {
            if ("Socket closed".equals(se.getMessage())) {
                //ignore
            } else {
                throw new RuntimeException(se);
            }
        } catch (IOException|InterruptedException|ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected byte[] getResponse(String request) {
        return Utf8.toBytes("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/xml; charset=UTF-8\r\n" +
                            "Connection: close\r\n" +
                            "Content-Length: 0\r\n" +
                            "\r\n");
    }

    protected byte[] getResponseBody() {
        return new byte[0];
    }

    public void stop() {
        if (!serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            serverThread.interrupt();
        } catch (Exception e) {
            //ignore
        }
    }

    public int getServerPort() {
        return serverSocket.getLocalPort();
    }

    public String getRequest() {
        try {
            return requestFuture.get(1, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError("Failed waiting for request. ", e);
        }
    }

}
