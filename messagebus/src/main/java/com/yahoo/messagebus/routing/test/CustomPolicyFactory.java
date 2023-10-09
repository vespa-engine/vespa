// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing.test;

import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingPolicy;
import com.yahoo.messagebus.test.SimpleProtocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class CustomPolicyFactory implements SimpleProtocol.PolicyFactory {

    private boolean selectOnRetry;
    private final List<Integer> consumableErrors = new ArrayList<Integer>();

    public CustomPolicyFactory() {
        this(true);
    }

    public CustomPolicyFactory(boolean selectOnRetry) {
        this(selectOnRetry, new ArrayList<Integer>());
    }

    public CustomPolicyFactory(boolean selectOnRetry, int consumableError) {
        this(selectOnRetry, Arrays.asList(consumableError));
    }

    public CustomPolicyFactory(boolean selectOnRetry, List<Integer> consumableErrors) {
        this.selectOnRetry = selectOnRetry;
        this.consumableErrors.addAll(consumableErrors);
    }

    public RoutingPolicy create(String param) {
        return new CustomPolicy(selectOnRetry, consumableErrors, parseRoutes(param));
    }

    public static List<Route> parseRoutes(String routes) {
        List<Route> ret = new ArrayList<Route>();
        if (routes != null && !routes.isEmpty()) {
            for (String route : routes.split(",")) {
                Route r = Route.parse(route);
                assert(route.equals(r.toString()));
                ret.add(r);
            }
        }
        return ret;
    }
}
