// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configkey.h"
#include <vespa/vespalib/util/time.h>

namespace config {

class ConfigSubscription;

struct SubscribeHandler
{
    /**
     * Subscribes to a spesific config given by a subscription.
     * If the subscribe call is successful, the callback handler will be called
     * with the new config.
     *
     * @param key the subscription key to subscribe to.
     * @param timeout the timeout of the subscribe call.
     * @return subscription object containing data relevant to client
     */
    virtual std::shared_ptr<ConfigSubscription> subscribe(const ConfigKey & key, vespalib::duration timeout) = 0;
    virtual ~SubscribeHandler() = default;
};

}

