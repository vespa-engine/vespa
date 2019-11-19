// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>

namespace config {

/**
 * A Waitable is a component that can be used to wait for some event to happen.
 */
struct Waitable
{
    using milliseconds = std::chrono::milliseconds;
    virtual bool wait(milliseconds) = 0;
    virtual ~Waitable() {}
};

} // namespace config

