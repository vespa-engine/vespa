// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "configsubscriptionset.h"
#include "configsubscription.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/iconfigmanager.h>
#include <vespa/config/common/iconfigcontext.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP(".config.subscription.configsubscriptionset");

using vespalib::duration;
using vespalib::steady_clock;
using vespalib::steady_time;

namespace config {

ConfigSubscriptionSet::ConfigSubscriptionSet(std::shared_ptr<IConfigContext> context)
    : _maxNapTime(vespalib::adjustTimeoutByDetectedHz(20ms)),
      _context(std::move(context)),
      _mgr(_context->getManagerInstance()),
      _currentGeneration(-1),
      _subscriptionList(),
      _state(OPEN),
      _lock(),
      _cond()
{ }

ConfigSubscriptionSet::~ConfigSubscriptionSet()
{
    close();
}

bool
ConfigSubscriptionSet::acquireSnapshot(duration timeout, bool ignoreChange)
{
    if (_state == CLOSED) {
        return false;
    } else if (_state == OPEN) {
        _state = FROZEN;
    }

    steady_time now = steady_clock::now();
    const steady_time deadline = now + timeout;
    int64_t lastGeneration = getGeneration();
    bool inSync = false;

    LOG(spam, "Going into nextConfig loop, time left is %f", vespalib::to_s(deadline - now));
    while (!isClosed() && (now <= deadline)) {
        size_t numChanged = 0;
        size_t numGenerationChanged = 0;
        bool generationsInSync = true;
        int64_t generation = -1;

        // Run nextUpdate on all subscribers to get them in sync.
        for (const auto & subscription : _subscriptionList) {

            if (!subscription->nextUpdate(getGeneration(), deadline) && !subscription->hasGenerationChanged()) {
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
            LOG(spam, "Previous generation is %" PRId64 ", updates is %" PRId64, lastGeneration, subscription->getGeneration());
            if (isGenerationNewer(subscription->getGeneration(), getGeneration())) {
                numGenerationChanged++;
            }
            if (generation < 0) {
                generation = subscription->getGeneration();
            }
            if (subscription->getGeneration() != generation) {
                generationsInSync = false;
            }
        }
        inSync = generationsInSync && (_subscriptionList.size() == numGenerationChanged) && (ignoreChange || numChanged > 0);
        lastGeneration = generation;
        now = steady_clock::now();
        std::unique_lock guard(_lock);
        if (!inSync && (now < deadline) && !isClosed()) {
            _cond.wait_for(guard, std::min(_maxNapTime, deadline - now));
        } else {
            break;
        }
    }

    bool updated = inSync && isGenerationNewer(lastGeneration, getGeneration());
    if (updated) {
        LOG(spam, "Config was updated from %" PRId64 " to %" PRId64, getGeneration(), lastGeneration);
        _currentGeneration.store(lastGeneration, std::memory_order_relaxed);
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
    {
        std::lock_guard guard(_lock);
        _state = CLOSED;
        _cond.notify_all();
    }
    for (const auto & subscription : _subscriptionList) {
        _mgr.unsubscribe(*subscription);
        subscription->close();
    }
}

std::shared_ptr<ConfigSubscription>
ConfigSubscriptionSet::subscribe(const ConfigKey & key, duration timeout)
{
    if (_state != OPEN) {
        throw ConfigRuntimeException("Adding subscription after calling nextConfig() is not allowed");
    }
    LOG(debug, "Subscribing with config Id(%s), defName(%s)", key.getConfigId().c_str(), key.getDefName().c_str());

    std::shared_ptr<ConfigSubscription> s = _mgr.subscribe(key, timeout);
    _subscriptionList.push_back(s);
    return s;
}

} // namespace config
