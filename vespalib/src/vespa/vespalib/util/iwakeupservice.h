// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "executor.h"
#include "idestructorcallback.h"

namespace vespalib {

/**
 * Interface to register for receiving wakeup calls.
 * The registration will last as long as the returned object is kept alive.
 **/
class IWakeupService {
public:
    virtual ~IWakeupService() = default;
    virtual std::shared_ptr<IDestructorCallback> registerForWakeup(IWakeup * toWakeup) = 0;
};

}
