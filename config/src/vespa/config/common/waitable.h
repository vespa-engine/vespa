// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>

namespace config {

/**
 * A Waitable is a component that can be used to wait for some event to happen.
 */
struct Waitable
{
    bool wait_for(vespalib::duration timeout) {
        return wait_until(vespalib::steady_clock::now() + timeout);
    }
    virtual bool wait_until(vespalib::steady_time deadline) = 0;
    virtual ~Waitable() = default;
};

} // namespace config

