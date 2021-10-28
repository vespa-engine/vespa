// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.logserver.protocol;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;

/**
 * A JRT based RPC server for handling log requests
 *
 * @author bjorncs
 */
public class RpcServer implements AutoCloseable {

    private final Supervisor supervisor;
    private final int listenPort;
    private Acceptor acceptor;

    public RpcServer(int listenPort) {
        supervisor = new Supervisor(new Transport("logserver-" + listenPort));
        this.listenPort = listenPort;
    }

    public void addMethod(Method method) {
        supervisor.addMethod(method);
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
