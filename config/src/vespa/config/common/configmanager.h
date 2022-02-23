// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/subscription/configsubscription.h>
#include "iconfigmanager.h"
#include "sourcefactory.h"
#include <map>
#include <mutex>

namespace config {

class SourceSpec;
struct TimingValues;

/**
 * An instance of this class represents a manager for config subscriptions, that use a common Source.
 * Getting and/or creating a new instance is done by calling the factory method getInstance(Source).
 *
 * The manager holds a reference to each subscription.
 */
class ConfigManager : public IConfigManager
{
public:
    ConfigManager(std::unique_ptr<SourceFactory> sourceFactory, int64_t initialGeneration);
    ~ConfigManager() override;

    ConfigSubscription::SP subscribe(const ConfigKey & key, vespalib::duration timeout) override;
    void unsubscribe(const ConfigSubscription & subscription) override;
    void reload(int64_t generation) override;

private:
    std::atomic<SubscriptionId>    _idGenerator;
    std::unique_ptr<SourceFactory> _sourceFactory;
    int64_t                        _generation;

    using SubscriptionMap = std::map<SubscriptionId, ConfigSubscription::SP>;
    SubscriptionMap _subscriptionMap;
    std::mutex _lock;
};

} // namespace config

