// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.slobrok.api.Mirror;
import java.util.logging.Level;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.VerbatimDirective;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This policy implements the logic to select a subset of services that matches a slobrok pattern.
 *
 * @author Simon Thoresen Hult
 */
public class SubsetServicePolicy implements DocumentProtocolRoutingPolicy {

    private static final Logger log = Logger.getLogger(SubsetServicePolicy.class.getName());
    private final int subsetSize;
    private final Map<String, CacheEntry> cache = new HashMap<>();

    /**
     * Creates an instance of a subset service policy. The parameter string is parsed as an integer number that is the
     * number of services to include in the set to choose from.
     *
     * @param param The number of services to include in the set.
     */
    SubsetServicePolicy(String param) {
        int subsetSize = 5;
        if (param != null && param.length() > 0) {
            try {
                subsetSize = Integer.parseInt(param);
            }
            catch (NumberFormatException e) {
                log.log(Level.WARNING, "Parameter '" + param + "' could not be parsed as an integer.", e);
            }
            if (subsetSize <= 0) {
                log.warning("Ignoring a request to set the subset size to " + subsetSize + " because it makes no " +
                            "sense. This routing policy will choose any one matching service.");
            }
        } else {
            log.warning("No parameter given to SubsetService policy, using default value " + subsetSize + ".");
        }
        this.subsetSize = subsetSize;
    }

    // Inherit doc from RoutingPolicy.
    public void select(RoutingContext ctx) {
        Route route = new Route(ctx.getRoute());
        route.setHop(0, getRecipient(ctx));
        ctx.addChild(route);
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
    private Hop getRecipient(RoutingContext ctx) {
        Hop hop = null;
        if (subsetSize > 0) {
            synchronized (this) {
                CacheEntry entry = update(ctx);
                if (!entry.recipients.isEmpty()) {
                    if (++entry.offset >= entry.recipients.size()) {
                        entry.offset = 0;
                    }
                    hop = new Hop(entry.recipients.get(entry.offset));
                }
            }
        }
        if (hop == null) {
            hop = new Hop(ctx.getRoute().getHop(0));
            hop.setDirective(ctx.getDirectiveIndex(), new VerbatimDirective("*"));
        }
        return hop;
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
            int pos = ctx.getMessageBus().getConnectionSpec().hashCode();
            for (int i = 0; i < subsetSize && i < arr.size(); ++i) {
                entry.recipients.add(Hop.parse(arr.get(((pos + i) & Integer.MAX_VALUE) % arr.size()).getName()));
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

    public void destroy() {
    }
}
