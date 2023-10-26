// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vbench {

/**
 * A Handler is a component to whom you can pass an object.
 **/
template <typename T>
struct Handler
{
    virtual void handle(std::unique_ptr<T> obj) = 0;
    virtual ~Handler() {}
};

} // namespace vbench
