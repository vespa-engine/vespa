// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/subscription/configsubscription.h>
#include "iconfigmanager.h"
#include "sourcefactory.h"
#include <vespa/vespalib/util/sync.h>
#include <map>

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
    ConfigManager(SourceFactory::UP sourceFactory, int64_t initialGeneration);
    ~ConfigManager();

    // Implements IConfigManager
    ConfigSubscription::SP subscribe(const ConfigKey & key, milliseconds timeoutInMillis) override;

    // Implements IConfigManager
    void unsubscribe(const ConfigSubscription::SP & subscription) override;

    // Implements IConfigManager
    void reload(int64_t generation) override;

private:
    std::atomic<SubscriptionId> _idGenerator;
    SourceFactory::UP _sourceFactory;
    int64_t _generation;

    typedef std::map<SubscriptionId, ConfigSubscription::SP> SubscriptionMap;
    SubscriptionMap _subscriptionMap;
    vespalib::Lock _lock;
    vespalib::Lock _firstLock;
};

} // namespace config

