// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container;

import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.QrConfig.Rpc;

/**
 * The http server singleton managing listeners for various ports,
 * and the threads used to respond to requests on the ports
 *
 * @author bratseth
 * @deprecated
 */
@SuppressWarnings("deprecation")
@Deprecated // TODO: Remove this when the last usage og getServerDiscriminator is removed
public class Server {

    //TODO: Make this final again.
    private static final Server instance = new Server();

    /** A short string which is different for all the qrserver instances on a given node. */
    private String localServerDiscriminator = "qrserver.0";

    private Server() { }

    public static Server get() {
        return instance;
    }

    public void initialize(QrConfig config) {
        localServerDiscriminator = config.discriminator();
    }

    /**
     * A string unique for this QRS on this server.
     *
     * @return a server specific string
     * @deprecated do not use
     */
    @Deprecated
    public String getServerDiscriminator() {
        return localServerDiscriminator;
    }

}
