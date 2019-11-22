// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configsubscriptionset.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/misc.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".config.subscription.configsubscriptionset");

using namespace std::chrono_literals;
using namespace std::chrono;

namespace config {

ConfigSubscriptionSet::ConfigSubscriptionSet(const IConfigContext::SP & context)
    : _context(context),
      _mgr(context->getManagerInstance()),
      _currentGeneration(-1),
      _subscriptionList(),
      _state(OPEN)
{ }

ConfigSubscriptionSet::~ConfigSubscriptionSet()
{
    close();
}

bool
ConfigSubscriptionSet::acquireSnapshot(milliseconds timeoutInMillis, bool ignoreChange)
{
    if (_state == CLOSED) {
        return false;
    } else if (_state == OPEN) {
        _state = FROZEN;
    }

    steady_clock::time_point startTime = steady_clock::now();
    milliseconds timeLeft = timeoutInMillis;
    int64_t lastGeneration = _currentGeneration;
    bool inSync = false;

    LOG(debug, "Going into nextConfig loop, time left is %ld", timeLeft.count());
    while (!isClosed() && (timeLeft.count() >= 0) && !inSync) {
        size_t numChanged = 0;
        size_t numGenerationChanged = 0;
        bool generationsInSync = true;
        int64_t generation = -1;

        // Run nextUpdate on all subscribers to get them in sync.
        for (const auto & subscription : _subscriptionList) {

            if (!subscription->nextUpdate(_currentGeneration, timeLeft) && !subscription->hasGenerationChanged()) {
                subscription->reset();
                continue;
            }

            const ConfigKey & key(subscription->getKey());
            if (subscription->hasChanged()) {
                LOG(spam, "Config subscription has changed id(%s), defname(%s)", key.getConfigId().c_str(), key.getDefName().c_str());
                numChanged++;
            } else {
                LOG(spam, "Config subscription did not change, id(%s), defname(%s)", key.getConfigId().c_str(), key.getDefName().c_str());
            }
            LOG(spam, "Previous generation is %" PRId64 ", updates is %" PRId64, generation, subscription->getGeneration());
            if (isGenerationNewer(subscription->getGeneration(), _currentGeneration)) {
                numGenerationChanged++;
            }
            if (generation < 0) {
                generation = subscription->getGeneration();
            }
            if (subscription->getGeneration() != generation) {
                generationsInSync = false;
            }
            // Adjust timeout
            timeLeft = timeoutInMillis - duration_cast<milliseconds>(steady_clock::now() - startTime);
        }
        inSync = generationsInSync && (_subscriptionList.size() == numGenerationChanged) && (ignoreChange || numChanged > 0);
        lastGeneration = generation;
        timeLeft = timeoutInMillis - duration_cast<milliseconds>(steady_clock::now() - startTime);
        if (!inSync && (timeLeft.count() > 0)) {
            std::this_thread::sleep_for(std::chrono::milliseconds(std::min(10l, timeLeft.count())));
        } else {
            break;
        }
    }

    bool updated = inSync && isGenerationNewer(lastGeneration, _currentGeneration);
    if (updated) {
        LOG(spam, "Config was updated from %" PRId64 " to %" PRId64, _currentGeneration, lastGeneration);
        _currentGeneration = lastGeneration;
        _state = CONFIGURED;
        for (const auto & subscription : _subscriptionList) {
            const ConfigKey & key(subscription->getKey());
            LOG(debug, "Updated config id(%s), defname(%s), has changed: %s, lastGenerationChanged: %" PRId64,
                key.getConfigId().c_str(),
                key.getDefName().c_str(),
                (subscription->hasChanged() ? "true" : "false"),
                subscription->getLastGenerationChanged());
            subscription->flip();
        }
    }
    return updated;
}

void
ConfigSubscriptionSet::close()
{
    _state = CLOSED;
    for (const auto & subscription : _subscriptionList) {
        _mgr.unsubscribe(subscription);
        subscription->close();
    }
}

bool
ConfigSubscriptionSet::isClosed() const
{
    return (_state.load(std::memory_order_relaxed) == CLOSED);
}

ConfigSubscription::SP
ConfigSubscriptionSet::subscribe(const ConfigKey & key, milliseconds timeoutInMillis)
{
    if (_state != OPEN) {
        throw ConfigRuntimeException("Adding subscription after calling nextConfig() is not allowed");
    }
    LOG(debug, "Subscribing with config Id(%s), defName(%s)", key.getConfigId().c_str(), key.getDefName().c_str());

    ConfigSubscription::SP s = _mgr.subscribe(key, timeoutInMillis);
    _subscriptionList.push_back(s);
    return s;
}

int64_t
ConfigSubscriptionSet::getGeneration() const
{
    return _currentGeneration;
}

} // namespace config
