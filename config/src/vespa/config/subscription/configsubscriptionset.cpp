// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configsubscriptionset.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/misc.h>
#include <thread>
#include <vespa/log/log.h>
LOG_SETUP(".config.subscription.configsubscriptionset");

using namespace std::chrono_literals;

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
ConfigSubscriptionSet::acquireSnapshot(uint64_t timeoutInMillis, bool ignoreChange)
{
    if (_state == CLOSED) {
        return false;
    } else if (_state == OPEN)
        _state = FROZEN;

    FastOS_Time timer;
    timer.SetNow();
    int timeLeft = timeoutInMillis;
    int64_t lastGeneration = _currentGeneration;
    bool inSync = false;

    for (SubscriptionList::iterator it(_subscriptionList.begin()), mt(_subscriptionList.end());
         it != mt;
         it++) {
        (*it)->reset();
    }

    LOG(debug, "Going into nextConfig loop, time left is %d", timeLeft);
    while (_state != CLOSED && timeLeft >= 0 && !inSync) {
        size_t numChanged = 0;
        size_t numGenerationChanged = 0;
        bool generationsInSync = true;
        int64_t generation = -1;

        // Run nextUpdate on all subscribers to get them in sync.
        for (SubscriptionList::iterator it(_subscriptionList.begin()), mt(_subscriptionList.end());
             it != mt;
             it++) {
            ConfigSubscription::SP subscription = *it;

            if (!subscription->nextUpdate(_currentGeneration, timeLeft))
                break;

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
            if (generation < 0)
                generation = subscription->getGeneration();
            if (subscription->getGeneration() != generation)
                generationsInSync = false;
            // Adjust timeout
            timeLeft = timeoutInMillis - static_cast<uint64_t>(timer.MilliSecsToNow());
        }
        inSync = generationsInSync && (_subscriptionList.size() == numGenerationChanged) && (ignoreChange || numChanged > 0);
        lastGeneration = generation;
        timeLeft = timeoutInMillis - static_cast<uint64_t>(timer.MilliSecsToNow());
        if (!inSync && timeLeft > 0) {
            std::this_thread::sleep_for(10ms);
        }
    }

    bool updated = inSync && isGenerationNewer(lastGeneration, _currentGeneration);
    if (updated) {
        LOG(spam, "Config was updated from %" PRId64 " to %" PRId64, _currentGeneration, lastGeneration);
        _currentGeneration = lastGeneration;
        _state = CONFIGURED;
        for (SubscriptionList::iterator it(_subscriptionList.begin()), mt(_subscriptionList.end());
             it != mt;
             it++) {
            (*it)->flip();
        }
    }
    return updated;
}

void
ConfigSubscriptionSet::close()
{
    _state = CLOSED;
    for (SubscriptionList::iterator it(_subscriptionList.begin()), mt(_subscriptionList.end()); it != mt; it++) {
        _mgr.unsubscribe(*it);
        (*it)->close();
    }
}

bool
ConfigSubscriptionSet::isClosed() const
{
    return (_state == CLOSED);
}

ConfigSubscription::SP
ConfigSubscriptionSet::subscribe(const ConfigKey & key, uint64_t timeoutInMillis)
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
