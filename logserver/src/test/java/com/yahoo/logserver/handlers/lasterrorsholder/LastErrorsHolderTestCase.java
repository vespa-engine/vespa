// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.lasterrorsholder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.Server;
import com.yahoo.text.Utf8;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class LastErrorsHolderTestCase {

    private static final int serverPort = 18324;
    private static final int lastErrorsHolderPort = 18326;
    private Server server;
    private Thread serverThread;
    private LastErrorsHolder lastErrorsHolder;

    @Before
    public void setUp() throws InterruptedException, IOException {
        server = Server.getInstance();
        server.initialize(serverPort);
        serverThread = new Thread(server);
        serverThread.start();
        lastErrorsHolder = new LastErrorsHolder(lastErrorsHolderPort);
    }

    @After
    public void tearDown() throws InterruptedException {
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join();
        }
        if (lastErrorsHolder != null) lastErrorsHolder.close();
    }

    public String connectAndGetLogMessages() throws InterruptedException, IOException {
        SocketChannel socket = null;
        Instant start = Instant.now();
        while (Instant.now().isBefore(start.plus(Duration.ofMinutes(1)))) {
            try {
                InetSocketAddress address = new InetSocketAddress("localhost", lastErrorsHolderPort);
                socket = SocketChannel.open(address);
                break;
            } catch (Exception e) {
                Thread.sleep(100);
            }
        }
        if (socket == null) {
            throw new RuntimeException("Could not connect to server");
        }

        ByteBuffer buf = ByteBuffer.allocateDirect(10000);
        int bytesRead = socket.read(buf);
        byte[] bytes = new byte[bytesRead];
        buf.position(0);
        buf.get(bytes);
        socket.close();

        return Utf8.toString(bytes);
    }


    @Test
    public void testLastErrorsHolder() throws IOException, InvalidLogFormatException, InterruptedException {
        LastErrorsHolder.Message logMessage1 = new LastErrorsHolder.Message(1433996283, "host1.yahoo.com", "container", LogLevel.ERROR
                .getName(), "foo");
        LastErrorsHolder.Message logMessage2 = new LastErrorsHolder.Message(1433996284, "host2.yahoo.com", "container", LogLevel.ERROR
                .getName(), "bar");
        LastErrorsHolder.Message logMessage3 = new LastErrorsHolder.Message(1433996285, "host2.yahoo.com", "container", LogLevel.INFO
                .getName(), "bar");

        LastErrorsHolder.Messages messages = new LastErrorsHolder.Messages();

        // No log messages yet
        String logs = connectAndGetLogMessages();
        final ObjectMapper mapper = new ObjectMapper();
        StringWriter stringWriter = new StringWriter();
        mapper.writeValue(stringWriter, messages);
        assertThat(logs, is(stringWriter.toString()));

        // Three messages, one is at level INFO
        lastErrorsHolder.doHandle(createLogMessage(logMessage1));
        lastErrorsHolder.doHandle(createLogMessage(logMessage2));
        lastErrorsHolder.doHandle(createLogMessage(logMessage3));
        messages = new LastErrorsHolder.Messages();
        messages.addMessage(logMessage1);
        messages.addMessage(logMessage2);
        messages.setNumberOfErrors(2);
        // Not adding logMessage3, since it is at level INFO

        logs = connectAndGetLogMessages();
        stringWriter = new StringWriter();
        mapper.writeValue(stringWriter, messages);
        assertThat(logs, is(stringWriter.toString()));
    }

    private LogMessage createLogMessage(LastErrorsHolder.Message message) throws InvalidLogFormatException {
        return createLogMessage(message.getTime(), message.getHostname(), message.getService(), message.getLogLevel(), message
                .getMessage());
    }

    private LogMessage createLogMessage(long time, String hostname, String service, String logLevel, String message) throws InvalidLogFormatException {
        return LogMessage.parseNativeFormat(String.format("%d\t%s\t1/1\t%s\tcomponent\t%s\t%s", time, hostname, service, logLevel
                .toLowerCase(), message));
    }

}
