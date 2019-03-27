// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.logserver.protocol;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.logserver.LogDispatcher;

/**
 * A JRT based RPC server for handling log requests
 *
 * @author bjorncs
 */
public class RpcServer implements AutoCloseable {

    private final Supervisor supervisor = new Supervisor(new Transport());
    private final int listenPort;
    private Acceptor acceptor;

    public RpcServer(int listenPort, LogDispatcher logDispatcher) {
        this.listenPort = listenPort;
        supervisor.addMethod(new ArchiveLogMessagesMethod(logDispatcher).methodDefinition());
    }

    public void start() {
        try {
            acceptor = supervisor.listen(new Spec(listenPort));
        } catch (ListenFailedException e) {
            throw new RuntimeException(e);
        }
    }

    int listenPort() {
        return acceptor.port();
    }

    @Override
    public void close() {
        if (acceptor != null) {
            acceptor.shutdown().join();
        }
        supervisor.transport().shutdown().join();
    }
}
