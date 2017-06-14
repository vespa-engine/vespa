// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.http.InternalServerException;
import com.yahoo.yolean.Exceptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fetches log entries from logserver with level errors and fatal. The logserver only returns
 * a log entry once over this API so doing repeated calls will not give the same results.
 *
 * @author dybis
 */
public class LogServerLogGrabber extends AbstractComponent {
    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(LogServerLogGrabber.class.getName());

    public LogServerLogGrabber() {}

    public String grabLog(Application application) {
        LogServerInfo logServerConnectionInfo = findLogserverConnectionInfo(application);
        log.log(LogLevel.DEBUG, "Requested error logs, pulling from logserver on " + logServerConnectionInfo);
        try {
            return readLog(logServerConnectionInfo.hostName, logServerConnectionInfo.port);
        } catch (IOException e) {
            throw new InternalServerException(Exceptions.toMessageString(e));
        }
    }

    private LogServerInfo findLogserverConnectionInfo(Application application) {
        List<LogServerInfo> logServerConnectionInfos = new ArrayList<>();
        application.getModel().getHosts()
                   .forEach(host -> host.getServices().stream()
                                        .filter(service -> service.getServiceType().equals("logserver"))
                                        .forEach(logService -> {
                                            Optional<Integer> logPort = getErrorLogPort(logService);
                                            logPort.ifPresent(port -> logServerConnectionInfos.add(new LogServerInfo(host.getHostname(), port)));
                                        }));

        if (logServerConnectionInfos.size() > 1) throw new RuntimeException("Found several log server ports");
        if (logServerConnectionInfos.size() == 0) throw new InternalServerException("Did not find any log server in config model");

        return logServerConnectionInfos.get(0);
    }

    // Protected to be able to test
    protected String readLog(String host, int port) throws IOException {
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

    private Optional<Integer> getErrorLogPort(ServiceInfo service) {
        return service.getPorts().stream()
                      .filter(port -> port.getTags().contains("last-errors-holder"))
                      .map(PortInfo::getPort)
                      .findFirst();
    }

    private class LogServerInfo {
        String hostName;
        int port;

        LogServerInfo(String hostName, int port) {
            this.hostName = hostName;
            this.port = port;
        }

        public String toString() {
            return hostName + ":" + port;
        }
    }
}
