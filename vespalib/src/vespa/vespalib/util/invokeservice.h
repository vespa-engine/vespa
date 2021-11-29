// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idestructorcallback.h"
#include <functional>

namespace vespalib {

/**
 * Interface to register for receiving regular invoke calls.
 * The registration will last as long as the returned object is kept alive.
 **/
class InvokeService {
public:
    virtual ~InvokeService() = default;
    virtual std::unique_ptr<IDestructorCallback> registerInvoke(std::function<void()> func) = 0;
};

}
