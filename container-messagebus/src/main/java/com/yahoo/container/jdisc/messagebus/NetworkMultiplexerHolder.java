// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.messagebus;

import com.yahoo.component.AbstractComponent;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.NetworkMultiplexer;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.shared.NullNetwork;
import com.yahoo.yolean.concurrent.Memoized;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds a reference to a singleton {@link NetworkMultiplexer}.
 *
 * @author jonmv
 */
public class NetworkMultiplexerHolder extends AbstractComponent {

    private final AtomicReference<RPCNetworkParams> params = new AtomicReference<>();
    private final Memoized<NetworkMultiplexer, RuntimeException> net = new Memoized<>(() -> NetworkMultiplexer.shared(newNetwork(params.get())),
                                                                                      NetworkMultiplexer::disown);

    /** Get the singleton RPCNetworkAdapter, creating it if this hasn't yet been done. */
    public NetworkMultiplexer get(RPCNetworkParams params) {
        this.params.set(params);
        return net.get();
    }

    private static Network newNetwork(RPCNetworkParams params) {
        return params.getSlobroksConfig() != null && params.getSlobroksConfig().slobrok().isEmpty()
               ? new NullNetwork() // For LocalApplication, test setup.
               : new RPCNetwork(params);
    }

    @Override
    public void deconstruct() {
        net.close();
    }

}
