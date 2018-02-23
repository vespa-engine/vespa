// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc.test;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.routing.RoutingSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.messagebus.test.SimpleProtocol;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * A simple test server implementation.
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 */
public class TestServer {

    private static final Logger log = Logger.getLogger(TestServer.class.getName());
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    public final VersionedRPCNetwork net;
    public final MessageBus mb;

    /**
     * Create a new test server.
     *
     * @param name             The service name prefix for this server.
     * @param table            The routing table spec to be used, may be null for no routing.
     * @param slobrok          The slobrok to register with (local).
     * @param protocol         The protocol that this server should support in addition to SimpleProtocol.
     */
    public TestServer(String name, RoutingTableSpec table, Slobrok slobrok, Protocol protocol) {
        this(new MessageBusParams().addProtocol(new SimpleProtocol()),
             new RPCNetworkParams()
                     .setIdentity(new Identity(name))
                     .setSlobrokConfigId(getSlobrokConfig(slobrok)));
        if (protocol != null) {
            mb.putProtocol(protocol);
        }
        if (table != null) {
            setupRouting(table);
        }
    }

    /**
     * Create a new test server.
     *
     * @param mbusParams The parameters for mesasge bus.
     * @param netParams  The parameters for the rpc network.
     */
    public TestServer(MessageBusParams mbusParams, RPCNetworkParams netParams) {
        net = new VersionedRPCNetwork(netParams);
        mb = new MessageBus(net, mbusParams);
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is called, it cleans up all its dependencies.
     * Even if you retain a reference to this object, all of its content is allowed to be garbage collected.
     *
     * @return True if content existed and was destroyed.
     */
    public boolean destroy() {
        if (!destroyed.getAndSet(true)) {
            mb.destroy();
            net.destroy();
            return true;
        }
        return false;
    }

    /**
     * Returns the raw config needed to connect to the given slobrok.
     *
     * @param slobrok The slobrok whose connection spec to include.
     * @return The raw config string.
     */
    public static String getSlobrokConfig(Slobrok slobrok) {
        return "raw:slobrok[1]\n" +
               "slobrok[0].connectionspec \"" + new Spec("localhost", slobrok.port()).toString() + "\"\n";
    }

    /**
     * Proxies the {@link MessageBus#setupRouting(RoutingSpec)} method by encapsulating the given table specification
     * within the required {@link RoutingSpec}.
     *
     * @param table The table to configure.
     */
    public void setupRouting(RoutingTableSpec table) {
        mb.setupRouting(new RoutingSpec().addTable(table));
    }

    /**
     * Wait for some pattern to resolve to some number of services.
     *
     * @param pattern Pattern to lookup in slobrok.
     * @param cnt     Number of services it must resolve to.
     * @return Whether or not the required state was reached.
     */
    public boolean waitSlobrok(String pattern, int cnt) {
        return waitState(new SlobrokState().add(pattern, cnt));
    }

    /**
     * Wait for a required slobrok state.
     *
     * @param slobrokState The state to wait for.
     * @return Whether or not the required state was reached.
     */
    public boolean waitState(SlobrokState slobrokState) {
        for (int i = 0; i < 6000 && !Thread.currentThread().isInterrupted(); ++i) {
            boolean done = true;
            for (String pattern : slobrokState.getPatterns()) {
                Mirror.Entry[] res = net.getMirror().lookup(pattern);
                if (res.length != slobrokState.getCount(pattern)) {
                    done = false;
                }
            }
            if (done) {
                return true;
            }
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                // ignore
            }
        }
        return false;
    }

    public static class VersionedRPCNetwork extends RPCNetwork {

        private Version version = Vtag.currentVersion;

        public VersionedRPCNetwork(RPCNetworkParams netParams) {
            super(netParams);
        }

        @Override
        protected Version getVersion() {
            return version;
        }

        public void setVersion(Version version) {
            this.version = version;
            flushTargetPool();
        }
    }
}
