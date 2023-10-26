// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>
#include <vespa/messagebus/routing/hop.h>
#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vector>
#include <map>
#include <mutex>

namespace documentapi {

/**
 * This policy implements the logic to prefer local services that matches a slobrok pattern.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class LocalServicePolicy : public mbus::IRoutingPolicy {
private:
    struct CacheEntry {
        uint32_t               _offset;
        uint32_t               _generation;
        std::vector<mbus::Hop> _recipients;

        CacheEntry();
    };

    std::mutex                   _lock;
    string                       _address;
    std::map<string, CacheEntry> _cache;

    /**
     * Returns the appropriate recipient hop for the given routing context. This method provides synchronized access to
     * the internal cache.
     *
     * @param ctx The routing context.
     * @return The recipient hop to use.
     */
    mbus::Hop getRecipient(mbus::RoutingContext &ctx);

    /**
     * Updates and returns the cache entry for the given routing context. This method assumes that synchronization is
     * handled outside of it.
     *
     * @param ctx The routing context.
     * @return The updated cache entry.
     */
    CacheEntry &update(mbus::RoutingContext &ctx);

    /**
     * Returns a cache key for this instance of the policy. Because behaviour is based on the hop in which the policy
     * occurs, the cache key is the hop string itself.
     *
     * @param ctx The routing context.
     * @return The cache key.
     */
    string getCacheKey(const mbus::RoutingContext &ctx) const;

    /**
     * Searches the given connection spec for a hostname or IP address. If an address is not found, this method returns
     * null.
     *
     * @param connection The connection spec to search.
     * @return The address, may be null.
     */
    static string toAddress(const string &connection);

public:
    /**
     * Constructs a policy that will choose local services that match the slobrok pattern in which this policy occured.
     * If no local service can be found, this policy simply returns the asterisk to allow the network to choose any.
     *
     * @param param The address to use for this, if empty this will resolve to hostname.
     */
    LocalServicePolicy(const string &param);
    ~LocalServicePolicy();
    void select(mbus::RoutingContext &context) override;
    void merge(mbus::RoutingContext &context) override;
};

}

