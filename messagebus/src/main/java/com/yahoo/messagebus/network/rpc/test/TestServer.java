// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc.test;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.local.LocalNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.routing.RoutingSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.messagebus.test.SimpleProtocol;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A simple test server implementation.
 *
 * @author havardpe
 */
public class TestServer {

    private static Logger log = Logger.getLogger(TestServer.class.getName());

    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    public final VersionedRPCNetwork net;
    public final MessageBus mb;

    /**
     * Create a new test server.
     *
     * @param name             the service name prefix for this server
     * @param table            the routing table spec to be used, may be null for no routing
     * @param slobrok          the slobrok to register with (local)
     * @param protocol         the protocol that this server should support in addition to SimpleProtocol
     */
    public TestServer(String name, RoutingTableSpec table, Slobrok slobrok, Protocol protocol) {
        this(new MessageBusParams().addProtocol(new SimpleProtocol()),
             new RPCNetworkParams()
                     .setIdentity(new Identity(name))
                     .setNumNetworkThreads(1)
                     .setSlobrokConfigId(getSlobrokConfig(slobrok)));
        if (protocol != null) {
            mb.putProtocol(protocol);
        }
        if (table != null) {
            setupRouting(table);
        }
        log.log(Level.INFO, "Running testServer '"+name+"' at "+net.getConnectionSpec()+", location broker at "+slobrok.port());
    }

    /** Creates a new test server. */
    public TestServer(MessageBusParams mbusParams, Slobrok slobrok) {
        this(mbusParams,
             new RPCNetworkParams()
             .setNumNetworkThreads(1)
             .setSlobrokConfigId(getSlobrokConfig(slobrok)));
        log.log(Level.INFO, "Running testServer <unnamed> at "+net.getConnectionSpec()+", location broker at "+slobrok.port());
    }

    /** Creates a new test server. */
    public TestServer(MessageBusParams mbusParams, RPCNetworkParams netParams) {
        net = new VersionedRPCNetwork(netParams);
        mb = new MessageBus(net, mbusParams);
    }

    /** Creates a new test server without network setup */
    public TestServer(MessageBusParams mbusParams) {
        mb = new MessageBus(new LocalNetwork(), mbusParams);
        net = null;
        log.log(Level.INFO, "Running testServer without network");
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is called, it cleans up all its dependencies.
     * Even if you retain a reference to this object, all of its content is allowed to be garbage collected.
     *
     * @return true if content existed and was destroyed
     */
    public boolean destroy() {
        if (!destroyed.getAndSet(true)) {
            if (net != null) {
                log.log(Level.INFO, "Destroy testServer '"+net.getIdentity().getServicePrefix()+"' at "+net.getConnectionSpec());
            } else {
                log.log(Level.INFO, "Destroy testServer without network");
            }
            mb.destroy();
            if (net != null)
                net.destroy();
            return true;
        }
        return false;
    }

    /**
     * Returns the raw config needed to connect to the given slobrok.
     *
     * @param slobrok the slobrok whose connection spec to include
     * @return the raw config string
     */
    public static String getSlobrokConfig(Slobrok slobrok) {
        return "raw:slobrok[1]\n" +
               "slobrok[0].connectionspec \"" + new Spec("localhost", slobrok.port()).toString() + "\"\n";
    }

    /**
     * Proxies the {@link MessageBus#setupRouting(RoutingSpec)} method by encapsulating the given table specification
     * within the required {@link RoutingSpec}.
     *
     * @param table the table to configure
     */
    public void setupRouting(RoutingTableSpec table) {
        mb.setupRouting(new RoutingSpec().addTable(table));
    }

    /**
     * Wait for some pattern to resolve to some number of services.
     *
     * @param pattern pattern to lookup in slobrok
     * @param cnt     number of services it must resolve to
     * @return Whether or not the required state was reached
     */
    public boolean waitSlobrok(String pattern, int cnt) {
        return waitState(new SlobrokState().add(pattern, cnt));
    }

    /**
     * Wait for a required slobrok state.
     *
     * @param slobrokState the state to wait for
     * @return whether or not the required state was reached
     */
    public boolean waitState(SlobrokState slobrokState) {
        int millis = 120 * 1000;
        for (int i = 0; i < millis && !Thread.currentThread().isInterrupted(); ++i) {
            boolean done = true;
            for (String pattern : slobrokState.getPatterns()) {
                List<Mirror.Entry> res = net.getMirror().lookup(pattern);
                if (res.size() != slobrokState.getCount(pattern)) {
                    done = false;
                }
            }
            if (done) {
                if (i > 50) log.log(Level.INFO, "waitState OK after "+i+" ms");
                return true;
            }
            if ((i % 1000) == 50) {
                log.log(Level.INFO, "waitState still waiting, "+i+" ms");
                var m = (Mirror) net.getMirror();
                m.dumpState();
            }
            try {
                Thread.sleep(1);
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
