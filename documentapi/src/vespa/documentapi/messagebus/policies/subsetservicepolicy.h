// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>
#include <vespa/messagebus/routing/hop.h>
#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <map>
#include <mutex>

namespace documentapi {

/**
 * This policy implements the logic to select a subset of services that matches a slobrok pattern.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class SubsetServicePolicy : public mbus::IRoutingPolicy {
private:
    struct CacheEntry {
        uint32_t               _offset;
        uint32_t               _generation;
        std::vector<mbus::Hop> _recipients;

        CacheEntry();
    };

    std::mutex                   _lock;
    uint32_t                     _subsetSize;
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

public:
    /**
     * Creates an instance of a subset service policy. The parameter string is parsed as an integer number that is the
     * number of services to include in the set to choose from.
     *
     * @param param The number of services to include in the set.
     */
    SubsetServicePolicy(const string &param);
    ~SubsetServicePolicy();
    void select(mbus::RoutingContext &context) override;
    void merge(mbus::RoutingContext &context) override;
};

}
