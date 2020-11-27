// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.RoutingNodeIterator;

import java.util.List;
import java.util.Map;

/**
 * Routing policy to load balance between nodes in a randomly distributed cluster, such as a docproc cluster.
 *
 * pattern=&lt;pattern&gt; (mandatory, determines the pattern of nodes to send to)<br>
 * slobroks=&lt;comma-separated connectionspecs&gt; (optional, list of slobroks to use to find the pattern)<br>
 * config=&lt;comma-separated list of config servers&gt; (optional, list of config servers to use to find slobrok config)
 *
 * If both slobroks and config is specified, the list from slobroks is used.
 *
 * @author Haakon Humberset
 */
public class LoadBalancerPolicy extends SlobrokPolicy {
    private final String session;
    private final String pattern;
    private final LoadBalancer loadBalancer;

    LoadBalancerPolicy(String param) {
        this(parse(param));
    }

    private LoadBalancerPolicy(Map<String, String> params) {
        super();

        String cluster = params.get("cluster");
        session = params.get("session");

        if (cluster == null) {
            throw new IllegalArgumentException("Required parameter 'cluster' not set");
        }

        if (session == null) {
            throw new IllegalArgumentException("Required parameter 'session' not set");
        }

        pattern = cluster + "/*/" + session;
        loadBalancer = new AdaptiveLoadBalancer(cluster);
    }

    @Override
    public void select(RoutingContext context) {
        LoadBalancer.Node node = getRecipient(context);

        if (node != null) {
            context.setContext(node);
            Route route = new Route(context.getRoute());
            route.setHop(0, Hop.parse(node.entry.getSpecString() + "/" + session));
            context.addChild(route);
        } else {
            context.setError(ErrorCode.NO_ADDRESS_FOR_SERVICE, "Could not resolve any nodes to send to in pattern " + pattern);
        }
    }

    /**
       Finds the TCP address of the target.

       @return Returns a hop representing the TCP address of the target, or null if none could be found.
    */
    private LoadBalancer.Node getRecipient(RoutingContext context) {
        List<Mirror.Entry> lastLookup = lookup(context, pattern);
        return loadBalancer.getRecipient(lastLookup);
    }

    public void merge(RoutingContext context) {
        RoutingNodeIterator it = context.getChildIterator();
        Reply reply = it.removeReply();
        LoadBalancer.Node target = (LoadBalancer.Node)context.getContext();

        boolean busy = false;
        for (int i = 0; i < reply.getNumErrors(); i++) {
            if (reply.getError(i).getCode() == ErrorCode.SESSION_BUSY) {
                busy = true;
            }
        }
        loadBalancer.received(target, busy);

        context.setReply(reply);
    }

    @Override
    public void destroy() {

    }

    // For testing
    LoadBalancer getLoadBalancer() { return loadBalancer; }
}
