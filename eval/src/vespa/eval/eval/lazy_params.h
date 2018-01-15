// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stddef.h>

namespace vespalib {

class Stash;

namespace eval {

class Value;

/**
 * Interface used to lazy-resolve parameters.
 **/
struct LazyParams {
    // used by compiled code to resolve lazy double-only parameters
    using resolve_function = double (*)(void *ctx, size_t idx);

    virtual const Value &resolve(size_t idx, Stash &stash) const = 0;
    virtual ~LazyParams();
};

} // namespace vespalib::eval
} // namespace vespalib
