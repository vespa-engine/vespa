// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configkeyset.h"
#include "configsnapshot.h"
#include <vespa/config/subscription/configsubscriptionset.h>

namespace config {

/**
 * The FixedConfigSubscriber takes an entires set of keys and subscribes to
 * all of them. Once this is done, it cannot be resubscribed.
 */
class FixedConfigSubscriber
{
public:
    FixedConfigSubscriber(const ConfigKeySet & keySet, std::shared_ptr<IConfigContext> context, vespalib::duration subscribeTimeout);
    bool nextGeneration(vespalib::duration timeout);
    void close();
    int64_t getGeneration() const;
    ConfigSnapshot getConfigSnapshot() const;
private:
    ConfigSubscriptionSet _set;
    std::vector<std::shared_ptr<ConfigSubscription>> _subscriptionList;
};

} // namespace config

