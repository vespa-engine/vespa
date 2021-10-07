// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace config {

/**
 * A Pollable is a component that can be polled, and returns either true or
 * false.
 */
struct Pollable
{
    virtual bool poll() = 0;
    virtual ~Pollable() {}
};

} // namespace config

