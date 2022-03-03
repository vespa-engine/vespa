// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "subscriptionid.h"
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/source.h>
#include <vespa/vespalib/util/time.h>
#include <atomic>

namespace config {

class IConfigHolder;
class ConfigUpdate;
class ConfigValue;

/**
 * A subscription can be polled for config updates, and handles interruption of
 * the nextUpdate call.
 */
class ConfigSubscription
{
public:
    typedef std::unique_ptr<ConfigSubscription> UP;
    typedef std::shared_ptr<ConfigSubscription> SP;

    ConfigSubscription(const SubscriptionId & id, const ConfigKey & key, std::shared_ptr<IConfigHolder> holder, std::unique_ptr<Source> source);
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
    bool isChanged() const noexcept { return _isChanged; }

    /**
     * Returns the last generation that actually changed the config.
     */
    int64_t getLastGenerationChanged() const noexcept { return _lastGenerationChanged; }

    /// Used by ConfigSubscriptionSet
    SubscriptionId getSubscriptionId() const noexcept { return _id; }
    const ConfigKey & getKey() const noexcept { return _key; }
    bool nextUpdate(int64_t generation, vespalib::steady_time deadline);
    int64_t getGeneration() const;
    bool hasChanged() const;
    bool hasGenerationChanged() const;
    void flip();
    void reset() noexcept { _isChanged = false; }
    void close();

    // Used by ConfigManager
    void reload(int64_t generation);

private:
    const SubscriptionId           _id;
    const ConfigKey                _key;
    std::unique_ptr<Source>        _source;
    std::shared_ptr<IConfigHolder> _holder;
    std::unique_ptr<ConfigUpdate>  _next;
    std::unique_ptr<ConfigUpdate>  _current;
    bool                           _isChanged;
    int64_t                        _lastGenerationChanged;
    std::atomic<bool>              _closed;
};

typedef std::vector<ConfigSubscription::SP> SubscriptionList;

} // namespace config

