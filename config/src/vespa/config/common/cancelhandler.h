// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace config {

class ConfigSubscription;

struct CancelHandler
{
    /**
     * Cancels this subscription. Once this operation is done, the handler
     * should have no knowledge of the subscription representing this id.
     *
     * @param subscription ConfigSubscription to cancel
     */
    virtual void unsubscribe(const ConfigSubscription & subscription) = 0;

    virtual ~CancelHandler() = default;
};

}

