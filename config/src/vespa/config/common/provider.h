// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace config {

/**
 * A Provider is a component from which you can request an object.
 **/
template <typename T>
struct Provider
{
    virtual std::unique_ptr<T> provide() = 0;
    virtual ~Provider() {}
};

} // namespace config

