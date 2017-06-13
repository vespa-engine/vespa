// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.metrics.MetricSet;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.RoutingNodeIterator;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class SearchRowPolicy implements DocumentProtocolRoutingPolicy {

    private static Logger log = Logger.getLogger(SearchRowPolicy.class.getName());
    private int minOk = 0; // Hide OUT_OF_SERVICE as long as this number of replies are something else.

    /**
     * Creates a search row policy that wraps the underlying search group policy in case the parameter is something
     * other than an empty string.
     *
     * @param param The number of minimum non-OOS replies that this policy requires.
     */
    public SearchRowPolicy(String param) {
        if (param != null && param.length() > 0) {
            try {
                minOk = Integer.parseInt(param);
            }
            catch (NumberFormatException e) {
                log.log(LogLevel.WARNING, "Parameter '" + param + "' could not be parsed as an integer.", e);
            }
            if (minOk <= 0) {
                log.log(LogLevel.WARNING, "Ignoring a request to set the minimum number of OK replies to " + minOk + " " +
                                          "because it makes no sense. This routing policy will not allow any recipient " +
                                          "to be out of service.");
            }
        }
    }

    @Override
    public void select(RoutingContext context) {
        context.addChildren(context.getMatchedRecipients());
        context.setSelectOnRetry(false);
        if (minOk > 0) {
            context.addConsumableError(ErrorCode.SERVICE_OOS);
        }
    }

    @Override
    public void merge(RoutingContext context) {
        if (minOk > 0) {
            Set<Integer> oosReplies = new HashSet<Integer>();
            int idx = 0;
            for (RoutingNodeIterator it = context.getChildIterator();
                 it.isValid(); it.next())
            {
                Reply ref = it.getReplyRef();
                if (ref.hasErrors() && DocumentProtocol.hasOnlyErrorsOfType(ref, ErrorCode.SERVICE_OOS)) {
                    oosReplies.add(idx);
                }
                ++idx;
            }
            if (context.getNumChildren() - oosReplies.size() >= minOk) {
                DocumentProtocol.merge(context, oosReplies);
                return;
            }
        }
        DocumentProtocol.merge(context);
    }

    @Override
    public void destroy() {
        // empty
    }

    @Override
    public MetricSet getMetrics() {
        return null;
    }
}
