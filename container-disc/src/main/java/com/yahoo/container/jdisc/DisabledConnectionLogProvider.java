// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.jdisc.http.server.jetty.VoidConnectionLog;

/**
 * @author bjorncs
 */
public class DisabledConnectionLogProvider implements Provider<ConnectionLog> {

    private static final ConnectionLog INSTANCE = new VoidConnectionLog();

    @Override public ConnectionLog get() { return INSTANCE; }
    @Override public void deconstruct() {}

}
