// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "configmanager.h"
#include "exceptions.h"
#include "configholder.h"
#include <thread>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".config.common.configmanager");


namespace config {

ConfigManager::ConfigManager(std::unique_ptr<SourceFactory> sourceFactory, int64_t initialGeneration)
    : _idGenerator(0),
      _sourceFactory(std::move(sourceFactory)),
      _generation(initialGeneration),
      _subscriptionMap(),
      _lock()
{ }

ConfigManager::~ConfigManager() = default;

ConfigSubscription::SP
ConfigManager::subscribe(const ConfigKey & key, vespalib::duration timeout)
{
    LOG(debug, "subscribing on def %s, configid %s", key.getDefName().c_str(), key.getConfigId().c_str());

    SubscriptionId id(_idGenerator.fetch_add(1));

    auto holder = std::make_shared<ConfigHolder>();
    std::unique_ptr<Source> source = _sourceFactory->createSource(holder, key);
    source->reload(_generation);

    source->getConfig();
    auto subscription = std::make_shared<ConfigSubscription>(id, key, holder, std::move(source));

    vespalib::steady_time endTime = vespalib::steady_clock::now() + timeout;
    while (vespalib::steady_clock::now() < endTime) {
        if (holder->poll())
            break;
        std::this_thread::sleep_for(10ms);
    }
    if (!holder->poll()) {
        std::ostringstream oss;
        oss << "Timed out while subscribing to '" << key.getDefNamespace() << "." << key.getDefName() << "', configid '" << key.getConfigId() << "'";
        throw ConfigTimeoutException(oss.str());
    }
    LOG(debug, "done subscribing");
    std::lock_guard guard(_lock);
    _subscriptionMap[id] = subscription;
    return subscription;
}

void
ConfigManager::unsubscribe(const ConfigSubscription & subscription)
{
    std::lock_guard guard(_lock);
    const SubscriptionId id(subscription.getSubscriptionId());
    if (_subscriptionMap.find(id) != _subscriptionMap.end())
        _subscriptionMap.erase(id);
}

void
ConfigManager::reload(int64_t generation)
{
    _generation = generation;
    std::lock_guard guard(_lock);
    for (auto & entry : _subscriptionMap) {
        entry.second->reload(_generation);
    }
}

} // namespace config
