// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * An AND policy is a routing policy that can be used to write simple routes that split a message between multiple other
 * destinations. It can either be configured in a routing config, which will then produce a policy that always selects
 * all configured recipients, or it can be configured using the policy parameter (i.e. a string following the name of
 * the policy). Note that configured recipients take precedence over recipients configured in the parameter string.
 *
 * @author Simon Thoresen Hult
 */
public class ANDPolicy implements DocumentProtocolRoutingPolicy {

    // A list of hops that are to always be selected when select() is invoked.
    private final List<Hop> hops = new ArrayList<>();

    /**
     * Constructs a new AND policy that requires all recipients to be ok for it to merge their replies to an ok reply.
     * I.e. all errors in all child replies are copied into the merged reply.
     *
     * @param param A string of recipients to select unless recipients have been configured.
     */
    public ANDPolicy(String param) {
        if (param == null || param.isEmpty()) {
            return;
        }
        Route route = Route.parse(param);
        for (int i = 0; i < route.getNumHops(); ++i) {
            hops.add(route.getHop(i));
        }
    }

    // Inherit doc from RoutingPolicy.
    public void select(RoutingContext context) {
        if (hops.isEmpty()) {
            context.addChildren(context.getAllRecipients());
        } else {
            for (Hop hop : hops) {
                Route route = new Route(context.getRoute());
                route.setHop(0, hop);
                context.addChild(route);
            }
        }
        context.setSelectOnRetry(false);
        context.addConsumableError(DocumentProtocol.ERROR_MESSAGE_IGNORED);
    }

    // Inherit doc from RoutingPolicy.
    public void merge(RoutingContext context) {
        DocumentProtocol.merge(context);
    }

    public void destroy() {
    }

}
