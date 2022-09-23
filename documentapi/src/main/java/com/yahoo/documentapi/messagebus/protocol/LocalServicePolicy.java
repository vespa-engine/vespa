// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.VerbatimDirective;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This policy implements the logic to prefer local services that matches a slobrok pattern.
 *
 * @author Simon Thoresen Hult
 */
public class LocalServicePolicy implements DocumentProtocolRoutingPolicy {

    private final String localAddress;
    private final Map<String, CacheEntry> cache = new HashMap<>();

    /**
     * Constructs a policy that will choose local services that match the slobrok pattern in which this policy occured.
     * If no local service can be found, this policy simply returns the asterisk to allow the network to choose any.
     *
     * @param param The address to use for this, if empty this will resolve to hostname.
     */
    LocalServicePolicy(String param) {
        localAddress = (param != null && param.length() > 0) ? param : null;
    }

    public void select(RoutingContext ctx) {
        Route route = new Route(ctx.getRoute());
        route.setHop(0, getRecipient(ctx));
        ctx.addChild(route);
    }

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
            Hop hop = new Hop(ctx.getRoute().getHop(0));
            hop.setDirective(ctx.getDirectiveIndex(), new VerbatimDirective("*"));
            return hop;
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

            List<Mirror.Entry> arr = ctx.getMirror().lookup(ctx.getHopPrefix() + "*" + ctx.getHopSuffix());
            String self = localAddress != null ? localAddress : toAddress(ctx.getMessageBus().getConnectionSpec());
            for (Mirror.Entry item : arr) {
                if (self.equals(toAddress(item.getSpecString()))) {
                    entry.recipients.add(Hop.parse(item.getName()));
                }
            }
        }
        return entry;
    }

    /**
     * Returns a cache key for this instance of the policy. Because behaviour is based on the hop in which the policy
     * occurs, the cache key is the hop string itself.
     *
     * @param ctx The routing context.
     * @return The cache key.
     */
    private String getCacheKey(RoutingContext ctx) {
        return ctx.getRoute().getHop(0).toString();
    }

    /**
     * Defines the necessary cache data.
     */
    private class CacheEntry {
        private final List<Hop> recipients = new ArrayList<>();
        private int generation = 0;
        private int offset = 0;
    }

    /**
     * Searches the given connection spec for a hostname or IP address. If an address is not found, this method returns
     * null.
     *
     * @param connection The connection spec to search.
     * @return The address, may be null.
     */
    private static String toAddress(String connection) {
        if (connection.startsWith("tcp/")) {
            int pos = connection.indexOf(':');
            if (pos > 4) {
                return connection.substring(4, pos);
            }
        }
        return null;
    }

    public void destroy() {
    }
}
