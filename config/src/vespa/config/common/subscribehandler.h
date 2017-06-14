// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/subscription/configsubscription.h>
#include <vespa/config/common/configkey.h>

namespace config {

struct SubscribeHandler
{
    /**
     * Subscribes to a spesific config given by a subscription.
     * If the subscribe call is successful, the callback handler will be called
     * with the new config.
     *
     * @param key the subscription key to subscribe to.
     * @param timeoutInMillis the timeout of the subscribe call.
     * @return subscription object containing data relevant to client
     */
    virtual ConfigSubscription::SP subscribe(const ConfigKey & key, uint64_t timeoutInMillis) = 0;
    virtual ~SubscribeHandler() { }
};

}

