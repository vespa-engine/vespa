// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <memory>
#include <vespa/config/common/iconfigholder.h>
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/source.h>
#include "subscriptionid.h"

#include <atomic>
#include <chrono>

namespace config {

/**
 * A subscription can be polled for config updates, and handles interruption of
 * the nextUpdate call.
 */
class ConfigSubscription
{
public:
    typedef std::unique_ptr<ConfigSubscription> UP;
    typedef std::shared_ptr<ConfigSubscription> SP;

    ConfigSubscription(const SubscriptionId & id, const ConfigKey & key, const IConfigHolder::SP & holder, Source::UP source);
    ~ConfigSubscription();

    /**
     * Fetches the appropriate ConfigValue.
     *
     * @return the current ConfigValue.
     */
    const ConfigValue & getConfig() const;

    /**
     * Checks whether or not the config has changed.
     *
     * @return true if changed, false if not.
     */
    bool isChanged() const;

    /**
     * Returns the last generation that actually changed the config.
     */
    int64_t getLastGenerationChanged() const;

    /// Used by ConfigSubscriptionSet
    SubscriptionId getSubscriptionId() const { return _id; }
    const ConfigKey & getKey() const;
    bool nextUpdate(int64_t generation, std::chrono::milliseconds timeoutInMillis);
    int64_t getGeneration() const;
    bool hasChanged() const;
    bool hasGenerationChanged() const;
    void flip();
    void reset();
    void close();

    // Used by ConfigManager
    void reload(int64_t generation);

private:
    const SubscriptionId _id;
    const ConfigKey _key;
    Source::UP _source;
    IConfigHolder::SP _holder;
    ConfigUpdate::UP _next;
    ConfigUpdate::UP _current;
    bool _isChanged;
    int64_t _lastGenerationChanged;
    std::atomic<bool> _closed;
};

typedef std::vector<ConfigSubscription::SP> SubscriptionList;

} // namespace config

