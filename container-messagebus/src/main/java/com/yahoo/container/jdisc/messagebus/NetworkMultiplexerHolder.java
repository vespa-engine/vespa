// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.messagebus;

import com.yahoo.component.AbstractComponent;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.NetworkMultiplexer;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.shared.NullNetwork;

/**
 * Holds a reference to a singleton {@link NetworkMultiplexer}.
 *
 * @author jonmv
 */
public class NetworkMultiplexerHolder extends AbstractComponent {

    private final Object monitor = new Object();
    private boolean destroyed = false;
    private NetworkMultiplexer net;

    /** Get the singleton RPCNetworkAdapter, creating it if this hasn't yet been done. */
    public NetworkMultiplexer get(RPCNetworkParams params) {
        synchronized (monitor) {
            if (destroyed)
                throw new IllegalStateException("Component already destroyed");

            return net = net != null ? net : NetworkMultiplexer.shared(newNetwork(params));
        }
    }

    private Network newNetwork(RPCNetworkParams params) {
        return params.getSlobroksConfig() != null && params.getSlobroksConfig().slobrok().isEmpty()
               ? new NullNetwork() // For LocalApplication, test setup.
               : new RPCNetwork(params);
    }

    @Override
    public void deconstruct() {
        synchronized (monitor) {
            if (net != null) {
                net.destroy();
                net = null;
            }
            destroyed = true;
        }
    }

}
