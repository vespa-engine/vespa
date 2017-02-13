// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.yolean.Exceptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.Optional;

/**
 * Fetches log entries from logserver with level errors and fatal. The logserver only return
 * a log entry once over this API so doing repeated call will not give the same results.
 *
 * @author dybis
 */
public class LogServerLogGrabber extends AbstractComponent {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LogServerLogGrabber.class.getName());

    public LogServerLogGrabber() {}

    private Optional<Integer> getErrorLogPort(ModelConfig.Hosts.Services service) {
        return service.ports().stream()
            .filter(port -> port.tags().toLowerCase().contains("last-errors-holder"))
            .map(ModelConfig.Hosts.Services.Ports::number)
            .findFirst();
    }

    private class LogServerConnectionInfo {
        String hostName;
        int port;
    }

    public String grabLog(Application application) {

        // TODO: Use model to get values (see how it's done in ApplicationConvergenceChecker)
        final ModelConfig config;
        try {
            config = application.getConfig(ModelConfig.class, "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final LogServerConnectionInfo logServerConnectionInfo = new LogServerConnectionInfo();

        config.hosts().stream()
                .forEach(host -> host.services().stream()
                        .filter(service -> service.type().equals("logserver"))
                        .forEach(logService -> {
                            Optional<Integer> logPort = getErrorLogPort(logService);
                            if (logPort.isPresent()) {
                                if (logServerConnectionInfo.hostName != null) {
                                    throw new RuntimeException("Found several log server ports");
                                }
                                logServerConnectionInfo.hostName = host.name();
                                logServerConnectionInfo.port = logPort.get();
                            }
                        }));

        if (logServerConnectionInfo.hostName == null) {
            throw new InternalServerException("Did not find any log server in config model");
        }
        log.log(LogLevel.DEBUG, "Requested error logs, pulling from logserver on " + logServerConnectionInfo.hostName + " "
                + logServerConnectionInfo.port);
        final String response;
        try {
            response = readLog(logServerConnectionInfo.hostName, logServerConnectionInfo.port);
            log.log(LogLevel.DEBUG, "Requested error logs was " + response.length() + " characters");
        } catch (IOException e) {
            throw new InternalServerException(Exceptions.toMessageString(e));
        }
        return response;
    }

    private String readLog(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        StringBuilder data = new StringBuilder();

        int bufferSize = 4096;
        int charsRead;
        do {
            char[] buffer = new char[bufferSize];
            charsRead = in.read(buffer);
            data.append(new String(buffer, 0, charsRead));
        } while (charsRead == bufferSize);
        in.close();
        socket.close();
        return data.toString();
    }
}
