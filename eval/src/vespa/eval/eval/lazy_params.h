// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include "value.h"

namespace vespalib {

class Stash;

namespace eval {

/**
 * Interface used to lazy-resolve parameters.
 **/
struct LazyParams {
    // used by compiled code to resolve lazy double-only parameters
    using resolve_function = double (*)(void *ctx, size_t idx);

    virtual const Value &resolve(size_t idx, Stash &stash) const = 0;
    virtual ~LazyParams();
};

/**
 * Simple wrapper for object parameters that are known up
 * front. Intended for convenience (testing), not performance.
 **/
struct SimpleObjectParams : LazyParams {
    std::vector<Value::CREF> params;
    explicit SimpleObjectParams(const std::vector<Value::CREF> &params_in)
        : params(params_in) {}
    ~SimpleObjectParams();
    const Value &resolve(size_t idx, Stash &stash) const override;
};

/**
 * Simple wrapper for number-only parameters that are known up
 * front. Intended for convenience (testing), not performance.
 **/
struct SimpleParams : LazyParams {
    std::vector<double> params;
    explicit SimpleParams(const std::vector<double> &params_in)
        : params(params_in) {}
    ~SimpleParams();
    const Value &resolve(size_t idx, Stash &stash) const override;
};

} // namespace vespalib::eval
} // namespace vespalib
