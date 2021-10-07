// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace config {

/**
 * An Interruptable is a component that can be notified that it should abort its current activities.
 */
struct Interruptable
{
    virtual void interrupt() = 0;
    virtual ~Interruptable() {}
};

} // namespace config

