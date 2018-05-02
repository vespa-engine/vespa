// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.subscription.ConfigURI;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.messagebus.routing.HopSpec;
import com.yahoo.messagebus.routing.RouteSpec;
import com.yahoo.messagebus.routing.RoutingSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;

/**
 * This class implements subscription to message bus config. To use configuration one must implement the {@link
 * ConfigHandler} interface.
 *
 * @author Simon Thoresen
 */
public class ConfigAgent implements ConfigSubscriber.SingleSubscriber<MessagebusConfig>{

    private final ConfigURI configURI;
    private final ConfigHandler handler;
    private ConfigSubscriber subscriber;

    /**
     * Create a config agent that will obtain config for the given handler and configure it programmatically.
     *
     * @param configId the config id we want to use
     * @param handler  the handler that should be configured
     */
    public ConfigAgent(String configId, ConfigHandler handler) {
        this.configURI = ConfigURI.createFromId(configId);
        this.handler = handler;
    }

    /**
     * Create a config agent that will obtain config for the given handler and configure it programmatically.
     *
     * @param configURI the config URI we want to use
     * @param handler  the handler that should be configured
     */
    public ConfigAgent(ConfigURI configURI, ConfigHandler handler) {
        this.configURI = configURI;
        this.handler = handler;
    }

    /**
     * Create a config agent that will configure the given handler with the given config.
     *
     * @param config the config we want to use
     * @param handler  the handler that should be configured
     */
    public ConfigAgent(MessagebusConfig config, ConfigHandler handler) {
        this.configURI = null;
        this.handler = handler;
        configure(config);
    }

    /**
     * Force reload config. Only necessary for testing or if subscribing to
     * config using files.
     */
    public void reload(long generation) {
        if (subscriber != null) {
            subscriber.reload(generation);
        }
    }

    /**
     * Start listening for config updates. This method will not return until the handler has been configured at least
     * once unless an exception is thrown.
     */
    public void subscribe() {
        if (configURI != null) {
            subscriber = new ConfigSubscriber(configURI.getSource());
            subscriber.subscribe(this, MessagebusConfig.class, configURI.getConfigId());
        }
    }

    @Override
    public void configure(MessagebusConfig config) {
        RoutingSpec routing = new RoutingSpec();
        for (int table = 0; table < config.routingtable().size(); table++) {
            MessagebusConfig.Routingtable tableConfig = config.routingtable(table);
            RoutingTableSpec tableSpec = new RoutingTableSpec(tableConfig.protocol());
            for (int hop = 0; hop < tableConfig.hop().size(); hop++) {
                MessagebusConfig.Routingtable.Hop hopConfig = tableConfig.hop(hop);
                HopSpec hopSpec = new HopSpec(hopConfig.name(), hopConfig.selector());
                for (int recipient = 0; recipient < hopConfig.recipient().size(); recipient++) {
                    hopSpec.addRecipient(hopConfig.recipient(recipient));
                }
                hopSpec.setIgnoreResult(hopConfig.ignoreresult());
                tableSpec.addHop(hopSpec);
            }
            for (int route = 0; route < tableConfig.route().size(); route++) {
                MessagebusConfig.Routingtable.Route routeConfig = tableConfig.route(route);
                RouteSpec routeSpec = new RouteSpec(routeConfig.name());
                for (int hop = 0; hop < routeConfig.hop().size(); hop++) {
                    routeSpec.addHop(routeConfig.hop(hop));
                }
                tableSpec.addRoute(routeSpec);
            }
            routing.addTable(tableSpec);
        }
        handler.setupRouting(routing);
    }

    /**
     * Shuts down the config agent by unsubscribing to the messagebus config.
     */
    public void shutdown() {
        if (subscriber != null) {
            subscriber.close();
        }
    }

}
