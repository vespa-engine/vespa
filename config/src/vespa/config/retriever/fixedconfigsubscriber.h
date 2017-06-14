// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/subscription/configsubscriptionset.h>
#include "configkeyset.h"
#include "configsnapshot.h"

namespace config {

/**
 * The FixedConfigSubscriber takes an entires set of keys and subscribes to
 * all of them. Once this is done, it cannot be resubscribed.
 */
class FixedConfigSubscriber
{
public:
    FixedConfigSubscriber(const ConfigKeySet & keySet, const IConfigContext::SP & context, int64_t subscribeTimeout);
    bool nextGeneration(int timeoutInMillis);
    void close();
    int64_t getGeneration() const;
    ConfigSnapshot getConfigSnapshot() const;
private:
    ConfigSubscriptionSet _set;
    std::vector<ConfigSubscription::SP> _subscriptionList;
};

} // namespace config

