// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace config {

/**
 * A Waitable is a component that can be used to wait for some event to happen.
 */
struct Waitable
{
    virtual bool wait(uint64_t timeoutInMillis) = 0;
    virtual ~Waitable() {}
};

} // namespace config

