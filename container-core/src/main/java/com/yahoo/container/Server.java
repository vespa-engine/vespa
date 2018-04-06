// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container;

import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.QrConfig.Rpc;
import com.yahoo.container.osgi.ContainerRpcAdaptor;

/**
 * The http server singleton managing listeners for various ports,
 * and the threads used to respond to requests on the ports
 *
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class Server {

    //TODO: Make this final again.
    private static Server instance = new Server();
    private ConfigSubscriber subscriber = new ConfigSubscriber();

    /** The OSGi container instance of this server */
    private Container container=Container.get();

    /** A short string which is different for all the qrserver instances on a given node. */
    private String localServerDiscriminator = "qrserver.0";

    /** Creates a new server instance. Not usually useful, use get() to get the current server */
    private Server() { }

    /** The number of currently active incoming search requests */
    public int searchQueriesInFlight() {
        //TODO: Implement
        return 0;
    }

    /**
     * An estimate of current number of connections. It is better to be
     * inaccurate than to acquire a lock per query fsync.
     *
     * @return The current number of open search connections
     */
    public int getCurrentConnections() {
        //TODO: Implement
        return 0;
    }

    public static Server get() {
        return instance;
    }


    private void initRpcServer(Rpc rpcConfig) {
        if (rpcConfig.enabled()) {
            ContainerRpcAdaptor rpcAdaptor = container.getRpcAdaptor();
            rpcAdaptor.listen(rpcConfig.port());
            rpcAdaptor.setSlobrokId(rpcConfig.slobrokId());
        }
    }

    /** Ugly hack, see Container.resetInstance
     **/
    static void resetInstance() {
        instance = new Server();
    }

    // TODO: Make independent of config
    public void initialize(QrConfig config) {
        localServerDiscriminator = config.discriminator();
        container.setupFileAcquirer(config.filedistributor());
        initRpcServer(config.rpc());
    }

    /**
     * A string unique for this QRS on this server.
     *
     * @return a server specific string
     */
    public String getServerDiscriminator() {
        return localServerDiscriminator;
    }

    public void shutdown() {
        if (subscriber!=null) subscriber.close();
    }

}
