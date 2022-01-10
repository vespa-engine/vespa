// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.messagebus;

import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.NetworkMultiplexer;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;

import java.util.function.Supplier;

/**
 * Injectable component which provides an {@link NetworkMultiplexer}, creating one if needed,
 * i.e., the first time this is created in a container--subsequent creations of this will reuse
 * the underlying network that was created initially. This breaks the DI pattern, but must be done
 * because the network is a unique resource which cannot exist in several versions simultaneously.
 *
 * @author jonmv
 */
public class NetworkMultiplexerProvider {

    private final Object monitor = new Object();
    private final Supplier<NetworkMultiplexer> nets;
    private NetworkMultiplexer net;

    @Inject
    public NetworkMultiplexerProvider(NetworkMultiplexerHolder net, ContainerMbusConfig mbusConfig) {
        this(net, mbusConfig, System.getProperty("config.id")); //:
    }

    public NetworkMultiplexerProvider(NetworkMultiplexerHolder net, ContainerMbusConfig mbusConfig, String identity) {
        this.nets = () -> net.get(asParameters(mbusConfig, identity));
    }

    public static RPCNetworkParams asParameters(ContainerMbusConfig mbusConfig, SlobroksConfig slobroksConfig, String identity) {
        return asParameters(mbusConfig, identity).setSlobroksConfig(slobroksConfig);
    }

    private static RPCNetworkParams asParameters(ContainerMbusConfig mbusConfig, String identity) {
        return new RPCNetworkParams().setSlobrokConfigId(identity)
                                     .setIdentity(new Identity(identity))
                                     .setListenPort(mbusConfig.port())
                                     .setNumTargetsPerSpec(mbusConfig.numconnectionspertarget())
                                     .setNumNetworkThreads(mbusConfig.numthreads())
                                     .setTransportEventsBeforeWakeup(mbusConfig.transport_events_before_wakeup())
                                     .setOptimization(RPCNetworkParams.Optimization.valueOf(mbusConfig.optimize_for().name()));
    }

    public NetworkMultiplexer net() {
        synchronized (monitor) {
            return net = net != null ? net : nets.get();
        }
    }

}
