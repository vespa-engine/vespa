// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#pragma once

#include <vespa/config/common/iconfigholder.h>
#include <vespa/config/common/configcontext.h>
#include "confighandle.h"
#include "subscriptionid.h"
#include "configsubscription.h"
#include "configprovider.h"

#include <atomic>

namespace config {

/**
 * A ConfigSubscriptionSet is a set of configs that can be subscribed to.
 */
class ConfigSubscriptionSet
{
public:
    using milliseconds = std::chrono::milliseconds;
    /**
     * Constructs a new ConfigSubscriptionSet object which can be used to subscribe for 1
     * or more configs from a specific source.
     *
     * @param context A ConfigContext shared between all subscriptions.
     */
    ConfigSubscriptionSet(const IConfigContext::SP & context);

    ~ConfigSubscriptionSet();

    /**
     * Return the current generation number for configs.
     *
     * @return generation number
     */
    int64_t getGeneration() const;

    /**
     * Closes the set, which will interrupt acquireSnapshot and unsubscribe all
     * configs currently subscribed for.
     */
    void close();

    /**
     * Checks if this subscription set is closed.
     */
    bool isClosed() const;

    // Helpers for doing the subscription
    ConfigSubscription::SP subscribe(const ConfigKey & key, milliseconds timeoutInMillis);

    // Tries to acquire a new snapshot of config within the timeout
    bool acquireSnapshot(milliseconds timeoutInMillis, bool requireDifference);

private:
    // Describes the state of the subscriber.
    enum SubscriberState { OPEN, FROZEN, CONFIGURED, CLOSED };

    IConfigContext::SP    _context;               // Context to keep alive managers.
    IConfigManager &      _mgr;                   // The config manager that we use.
    int64_t               _currentGeneration;     // Holds the current config generation.
    SubscriptionList      _subscriptionList;      // List of current subscriptions.

    std::atomic<SubscriberState> _state;              // Current state of this subscriber.
};

} // namespace config

