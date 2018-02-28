// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.rpc.RPCCommunicator;

/**
 * Options for simulated content nodes that are registered in Slobrok and communicate
 * over regular RPC.
 */
public class DummyVdsNodeOptions {
    // 0 - 4.1, 1 - 4.2-5.0.10, 2 - 5.0.11+, 3 - 6.220+
    public int stateCommunicationVersion = RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_VERSION;
}
