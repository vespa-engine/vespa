// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing.test;

import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.RoutingNodeIterator;
import com.yahoo.messagebus.routing.RoutingPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class CustomPolicy implements RoutingPolicy  {

    private boolean selectOnRetry;
    private final List<Integer> consumableErrors = new ArrayList<Integer>();
    private final List<Route> routes = new ArrayList<Route>();

    public CustomPolicy(boolean selectOnRetry, List<Integer> consumableErrors, List<Route> routes) {
        this.selectOnRetry = selectOnRetry;
        this.consumableErrors.addAll(consumableErrors);
        this.routes.addAll(routes);
    }

    public void select(RoutingContext context) {
        context.trace(1, "Selecting " + routes + ".");
        context.setSelectOnRetry(selectOnRetry);
        for (int e : consumableErrors) {
            context.addConsumableError(e);
        }
        context.addChildren(routes);
    }

    public void merge(RoutingContext context) {
        List<String> lst = new ArrayList<String>();
        Reply ret = new EmptyReply();
        for (RoutingNodeIterator it = context.getChildIterator();
             it.isValid(); it.next())
        {
            lst.add(it.getRoute().toString());
            Reply reply = it.getReplyRef();
            for (int i = 0; i < reply.getNumErrors(); ++i) {
                ret.addError(reply.getError(i));
            }
        }
        context.setReply(ret);
        context.trace(1, "Merged " + lst + ".");
    }

    @Override
    public void destroy() {
    }

}
