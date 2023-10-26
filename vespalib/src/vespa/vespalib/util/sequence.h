// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib {

/**
 * abstract interface for an iterable sequence.
 **/
template <typename T>
struct Sequence
{
    using UP = std::unique_ptr<Sequence>;

    virtual bool valid() const = 0;
    virtual T get() const = 0;
    virtual void next() = 0;
    virtual ~Sequence() {}
};

} // namespace vespalib

