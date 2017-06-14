// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.lasterrorsholder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.io.Connection;
import com.yahoo.io.ConnectionFactory;
import com.yahoo.io.Listener;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.handlers.AbstractLogHandler;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The LastErrorsHolder handler is used for holding the last n
 * messages at level error or higher. Connecting to this handler
 * will return a Json object with the last errors (default is last 100 errors)
 *
 * @author hmusum
 */
public class LastErrorsHolder extends AbstractLogHandler implements ConnectionFactory {

    private static final Logger log = Logger.getLogger(LastErrorsHolder.class.getName());
    private static final int maxNumErrors = 100;
    private final Object lock = new Object();

    private int port;
    private Listener listener;

    private final ArrayList<LogMessage> errors = new ArrayList<>();
    private int numberOfErrors = 0;

    /**
     * @param port The port to which this handler listens to.
     */
    public LastErrorsHolder(int port) throws IOException {
        this.port = port;
        listen(port);
    }

    public void listen(int port) throws IOException {
        if (listener != null) {
            throw new IllegalStateException("already listening to port " + this.port);
        }
        listener = new Listener("last-errors-holder");
        listener.listen(this, port);
        listener.start();
        log.log(LogLevel.CONFIG, "port=" + port);
    }

    public boolean doHandle(LogMessage msg) {
        if (msg.getLevel().equals(LogLevel.ERROR) || msg.getLevel().equals(LogLevel.FATAL)) {
            synchronized (lock) {
                numberOfErrors++;
                if (errors.size() < maxNumErrors) {
                    errors.add(msg);
                } else if (numberOfErrors == maxNumErrors) {
                    log.log(LogLevel.DEBUG, String.format("Not storing errors, have reached maximum number of errors: %d, total number of errors received: %d",
                            maxNumErrors, numberOfErrors));
                }
            }
        }
        return true;
    }

    public void close() {
        try {
            listener.interrupt();
            listener.join();
            log.log(LogLevel.DEBUG, "listener stopped");
        } catch (InterruptedException e) {
            log.log(LogLevel.WARNING, "listener was interrupted", e);
        }
    }

    public void flush() {
    }

    /**
     * Factory method for creating new connections. Since we just return a result
     * when client connection happens, we also write the result here
     *
     * @param socket   The new SocketChannel
     * @param listener The Listener instance we want to use
     */
    public Connection newConnection(SocketChannel socket, Listener listener) {
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "New last-errors-holder connection: " + socket);
        }
        LastErrorsHolderConnection connection = new LastErrorsHolderConnection(socket);
        synchronized (lock) {
            Messages messages = new Messages();
            for (LogMessage error : errors) {
                messages.addMessage(
                        new Message(error.getTime()/1000,
                                error.getHost(),
                                error.getService(),
                                error.getLevel().getName(),
                                error.getPayload()));
            }
            messages.setNumberOfErrors(numberOfErrors);

            try {
                ObjectMapper mapper = new ObjectMapper();
                StringWriter stringWriter = new StringWriter();
                mapper.writeValue(stringWriter, messages);
                connection.enqueue(StandardCharsets.UTF_8.encode(stringWriter.toString()));
            } catch (IOException e) {
                log.log(LogLevel.WARNING, "Could not enqueue log message", e);
            }

            errors.clear();
            numberOfErrors = 0;
        }

        return connection;
    }

    public String toString() {
        return LastErrorsHolder.class.getName();
    }


    static class Messages {
        private final List<Message> messages = new ArrayList<>();
        private long errorCount = 0; // There might be more errors than number of messages

        void addMessage(Message message) {
            messages.add(message);
        }

        void setNumberOfErrors(long errorCount) {
            this.errorCount = errorCount;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public long getErrorCount() {
            return errorCount;
        }
    }

    static class Message {
        private final long time;
        private final String hostname;
        private final String service;
        private final String logLevel;
        private final String message;

        Message(long time, String hostname, String service, String logLevel, String message) {
            this.time = time;
            this.hostname = hostname;
            this.service = service;
            this.logLevel = logLevel;
            this.message = message;
        }

        public long getTime() {
            return time;
        }

        public String getMessage() {
            return message;
        }

        public String getLogLevel() {
            return logLevel;
        }

        public String getHostname() {
            return hostname;
        }

        public String getService() {
            return service;
        }
    }

}
