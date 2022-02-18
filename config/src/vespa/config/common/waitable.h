// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>

namespace config {

/**
 * A Waitable is a component that can be used to wait for some event to happen.
 */
struct Waitable
{
    virtual bool wait(vespalib::duration timeout) = 0;
    virtual ~Waitable() = default;
};

} // namespace config

