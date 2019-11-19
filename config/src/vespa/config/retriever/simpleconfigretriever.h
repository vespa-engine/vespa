// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/subscription/configsubscriptionset.h>
#include <vespa/config/common/timingvalues.h>
#include "configkeyset.h"
#include "configsnapshot.h"

namespace config {

/**
 * The SimpleConfigRetriever takes an entires set of keys and subscribes to
 * all of them. Once this is done, it cannot be resubscribed. You can poll this
 * for new snapshots.
 */
class SimpleConfigRetriever
{
public:
    typedef std::unique_ptr<SimpleConfigRetriever> UP;
    using milliseconds = std::chrono::milliseconds;

    SimpleConfigRetriever(const ConfigKeySet & keySet,
                          const IConfigContext::SP & context,
                          milliseconds subscribeTimeout = DEFAULT_SUBSCRIBE_TIMEOUT);

    /**
     * Attempt retrieving a snapshot of configs.
     * @param timeoutInMillis The amount of time to wait for a new snapshot.
     * @return A new snapshot. The snapshot is empty if timeout was reached or
     *         if the retriever was closed.
     */
    ConfigSnapshot getConfigs(milliseconds timeoutInMillis = DEFAULT_GETCONFIGS_TIMEOUT);
    void close();
    bool isClosed() const;

private:
    ConfigSubscriptionSet _set;
    std::vector<ConfigSubscription::SP> _subscriptionList;
};

} // namespace config

