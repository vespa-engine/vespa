// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.vespa.config.content.DistributionConfig;

import static java.util.Objects.requireNonNull;

/**
 * @author Einar M R Rosenvinge
 */
public class MessageBusParams extends DocumentAccessParams {

    private String routingConfigId = null;
    private String protocolConfigId = null;
    private DocumentProtocolPoliciesConfig policiesConfig = null;
    private DistributionConfig distributionConfig = null;
    private String route = "route:default";
    private String routeForGet = "route:default-get";
    private int traceLevel = 0;
    private RPCNetworkParams rpcNetworkParams = new RPCNetworkParams();
    private com.yahoo.messagebus.MessageBusParams mbusParams = new com.yahoo.messagebus.MessageBusParams();
    private SourceSessionParams sourceSessionParams = new SourceSessionParams();
    private LoadTypeSet loadTypes;

    public MessageBusParams() {
        this(new LoadTypeSet());
    }

    public MessageBusParams(LoadTypeSet loadTypes) {
        this.loadTypes = loadTypes;
    }

    /**
     *
     * @return Returns the set of load types accepted by this Vespa installation
     */
    public LoadTypeSet getLoadTypes() {
        return loadTypes;
    }

    /**
     * Returns the id to resolve to routing config.
     *
     * @return The config id.
     */
    public String getRoutingConfigId() {
        return routingConfigId;
    }

    /**
     * Sets the id to resolve to routing config. This has a proper default value that holds for Vespa applications, and
     * can therefore be left unset.
     *
     * @param configId The config id.
     * @return This object for chaining.
     */
    public MessageBusParams setRoutingConfigId(String configId) {
        routingConfigId = configId;
        return this;
    }

    /**
     * Returns the id to resolve to protocol config.
     *
     * @return The config id.
     */
    public String getProtocolConfigId() {
        return protocolConfigId;
    }

    /**
     * Sets the id to resolve to protocol config. This has a proper default value that holds for Vespa applications,
     * and can therefore be left usnet.
     *
     * @param configId The config id.
     * @return This, to allow chaining.
     */
    public MessageBusParams setProtocolConfigId(String configId) {
        protocolConfigId = configId;
        return this;
    }

    /** Sets the config used by the {@link DocumentProtocol} policies. */
    public MessageBusParams setDocumentProtocolPoliciesConfig(DocumentProtocolPoliciesConfig policiesConfig,
                                                              DistributionConfig distributionConfig) {
        this.policiesConfig = requireNonNull(policiesConfig);
        this.distributionConfig = requireNonNull(distributionConfig);
        return this;
    }

    /**
     * Sets the name of the route to send appropriate requests to. This is a convenience method for prefixing a route
     * with "route:", and using {@link #setRoute} instead.
     *
     * @param routeName The route name.
     * @return This object for chaining.
     */
    public MessageBusParams setRouteName(String routeName) {
        return setRoute("route:" + routeName);
    }

    /**
     * Sets the route string to send all requests to. This string will be parsed as a route string, so setting a route
     * name directly will not necessarily have the intended consequences. Use "route:&lt;routename&gt;" syntax for route
     * names, or the convenience method {@link #setRouteName} for this.
     *
     * @param route The route string.
     * @return This object for chaining.
     */
    public MessageBusParams setRoute(String route) {
        this.route = route;
        return this;
    }

    public MessageBusParams setRouteNameForGet(String routeName) {
        return setRouteForGet("route:" + routeName);
    }
    public MessageBusParams setRouteForGet(String route) {
        this.routeForGet = route;
        return this;
    }

    /**
     * Returns the route string that all requests will be sent to.
     *
     * @return The route string.
     */
    public String getRoute() {
        return route;
    }

    public String getRouteForGet() {
        return routeForGet;
    }

    /**
     * Returns the trace level to use when sending.
     *
     * @return The trace level.
     */
    public int getTraceLevel() {
        return traceLevel;
    }

    /**
     * Sets the trace level to use when sending.
     *
     * @param traceLevel The trace level.
     * @return This object for chaining.
     */
    public MessageBusParams setTraceLevel(int traceLevel) {
        this.traceLevel = traceLevel;
        return this;
    }

    /**
     * Returns the params object used to instantiate the rpc network layer for message bus.
     *
     * @return The params object.
     */
    public RPCNetworkParams getRPCNetworkParams() {
        return rpcNetworkParams;
    }

    /**
     * Sets the params object used to instantiate the rpc network layer for message bus.
     *
     * @param params The params object.
     * @return This object for chaining.
     */
    public MessageBusParams setRPCNetworkParams(RPCNetworkParams params) {
        rpcNetworkParams = new RPCNetworkParams(params);
        return this;
    }

    /**
     * Returns the params object used to instantiate the message bus.
     *
     * @return The params object.
     */
    public com.yahoo.messagebus.MessageBusParams getMessageBusParams() {
        return mbusParams;
    }

    /**
     * Sets the params object used to instantiate the message bus.
     *
     * @param params The params object.
     * @return This object for chaining.
     */
    public MessageBusParams setMessageBusParams(com.yahoo.messagebus.MessageBusParams params) {
        mbusParams = new com.yahoo.messagebus.MessageBusParams(params);
        return this;
    }

    /**
     * Returns a reference to the extended source session params object.
     *
     * @return The params object.
     */
    public SourceSessionParams getSourceSessionParams() {
        return sourceSessionParams;
    }

    /**
     * Sets the extended source session params.
     *
     * @param params The params object.
     * @return This object for chaining.
     */
    public MessageBusParams setSourceSessionParams(SourceSessionParams params) {
        sourceSessionParams = new SourceSessionParams(params);
        return this;
    }
}
