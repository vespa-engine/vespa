// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.JRTConnection;
import com.yahoo.vespa.config.JRTConnectionPool;

import java.util.List;


/**
 * A pool of JRT connections to a set of file distribution source (one or more config servers).
 * Used by file distribution clients, where the source that can serve a file reference might be
 * different for each file reference (unlike config requests, where all requests should be served by the same source).
 * A new connection is chosen randomly when calling {#link {@link #switchConnection(Connection failingConnection)}}.
 * Unlike JRTConnectionPool there is no state that holds the 'current' connection, a new connection is picked
 * randomly if {@link #getCurrent()} is called.
 *
 * @author hmusum
 */
public class FileDistributionConnectionPool extends JRTConnectionPool {

    public FileDistributionConnectionPool(ConfigSourceSet sourceSet, Supervisor supervisor) {
        super(sourceSet, supervisor);
    }

    @Override
    public synchronized JRTConnection getCurrent() {
        return pickNewConnectionRandomly(getSources());
    }

    @Override
    public synchronized JRTConnection switchConnection(Connection failingConnection) {
        if (getSources().size() <= 1) return getCurrent();

        List<JRTConnection> sourceCandidates = getSources();
        sourceCandidates.remove(failingConnection);
        return pickNewConnectionRandomly(sourceCandidates);
    }

}
