// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configkey.h"
#include <vespa/config/subscription/configsubscription.h>

namespace config {

struct SubscribeHandler
{
    using milliseconds = std::chrono::milliseconds;
    /**
     * Subscribes to a spesific config given by a subscription.
     * If the subscribe call is successful, the callback handler will be called
     * with the new config.
     *
     * @param key the subscription key to subscribe to.
     * @param timeoutInMillis the timeout of the subscribe call.
     * @return subscription object containing data relevant to client
     */
    virtual ConfigSubscription::SP subscribe(const ConfigKey & key, milliseconds timeoutInMillis) = 0;
    virtual ~SubscribeHandler() { }
};

}

