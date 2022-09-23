// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This policy implements round-robin selection of the configured recipients that are currently registered in slobrok.
 *
 * @author Simon Thoresen Hult
 */
public class RoundRobinPolicy implements DocumentProtocolRoutingPolicy {

    private final Map<String, CacheEntry> cache = new HashMap<>();

    // Inherit doc from RoutingPolicy.
    public void select(RoutingContext ctx) {
        Hop hop = getRecipient(ctx);
        if (hop != null) {
            Route route = new Route(ctx.getRoute());
            route.setHop(0, hop);
            ctx.addChild(route);
        } else {
            Reply reply = new EmptyReply();
            reply.addError(new Error(ErrorCode.NO_ADDRESS_FOR_SERVICE,
                                     "None of the configured recipients are currently available."));
            ctx.setReply(reply);
        }
    }

    // Inherit doc from RoutingPolicy.
    public void merge(RoutingContext ctx) {
        DocumentProtocol.merge(ctx);
    }

    /**
     * Returns the appropriate recipient hop for the given routing context. This method provides synchronized access to
     * the internal cache.
     *
     * @param ctx The routing context.
     * @return The recipient hop to use.
     */
    private synchronized Hop getRecipient(RoutingContext ctx) {
        CacheEntry entry = update(ctx);
        if (entry.recipients.isEmpty()) {
            return null;
        }
        if (++entry.offset >= entry.recipients.size()) {
            entry.offset = 0;
        }
        return new Hop(entry.recipients.get(entry.offset));
    }

    /**
     * Updates and returns the cache entry for the given routing context. This method assumes that synchronization is
     * handled outside of it.
     *
     * @param ctx The routing context.
     * @return The updated cache entry.
     */
    private CacheEntry update(RoutingContext ctx) {
        String key = getCacheKey(ctx);
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            entry = new CacheEntry();
            cache.put(key, entry);
        }

        int upd = ctx.getMirror().updates();
        if (entry.generation != upd) {
            entry.generation = upd;
            entry.recipients.clear();
            for (int i = 0; i < ctx.getNumRecipients(); ++i) {
                List<Mirror.Entry> arr = ctx.getMirror().lookup(ctx.getRecipient(i).getHop(0).toString());
                for (Mirror.Entry item : arr) {
                    entry.recipients.add(Hop.parse(item.getName()));
                }
            }
        }
        return entry;
    }

    /**
     * Returns a cache key for this instance of the policy. Because behaviour is based on the recipient list of this
     * policy, the cache key is the concatenated string of recipient routes.
     *
     * @param ctx The routing context.
     * @return The cache key.
     */
    private String getCacheKey(RoutingContext ctx) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < ctx.getNumRecipients(); ++i) {
            ret.append(ctx.getRecipient(i).getHop(0).toString()).append(" ");
        }
        return ret.toString();
    }

    /**
     * Defines the necessary cache data.
     */
    private class CacheEntry {
        private final List<Hop> recipients = new ArrayList<Hop>();
        private int generation = 0;
        private int offset = 0;
    }

    public void destroy() {
    }

}
