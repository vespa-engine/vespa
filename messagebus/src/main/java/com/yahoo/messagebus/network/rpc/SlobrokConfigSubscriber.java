// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.cloud.config.SlobroksConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements subscription to slobrok config.
 *
 * @author Simon Thoresen Hult
 */
public class SlobrokConfigSubscriber implements ConfigSubscriber.SingleSubscriber<SlobroksConfig>{

    private static final Logger log = Logger.getLogger(SlobrokConfigSubscriber.class.getName());
    private final SlobrokList slobroks = new SlobrokList();
    private ConfigSubscriber subscriber;

    /**
     * Constructs a new config subscriber for a given config id.
     *
     * @param configId the id of the config to subscribe to
     */
    public SlobrokConfigSubscriber(String configId) {
        log.log(Level.FINE, "new slobrok config subscriber with config id: "+configId);
        subscriber = new ConfigSubscriber();
        subscriber.subscribe(this, SlobroksConfig.class, configId);
    }

    public SlobrokConfigSubscriber(SlobroksConfig slobroksConfig) {
        log.log(Level.FINE, "new slobrok config subscriber with fixed list: "+slobroksConfig);
        configure(slobroksConfig);
    }

    @Override
    public void configure(SlobroksConfig config) {
        String[] list = new String[config.slobrok().size()];
        for(int i = 0; i < config.slobrok().size(); i++) {
            list[i] = config.slobrok(i).connectionspec();
        }
        slobroks.setup(list);
    }

    /**
     * Returns the current slobroks config as an array of connection spec strings.
     *
     * @return The slobroks config.
     */
    public SlobrokList getSlobroks() {
        return new SlobrokList(slobroks);
    }

    /**
     * Shuts down the config subscription by unsubscribing to the slobroks config.
     */
    public void shutdown() {
        if (subscriber != null) {
            subscriber.close();
        }
    }

}
