// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "function.h"
#include <vespa/vespalib/util/optimized.h>
#include <memory>
#include <cassert>
#include <cmath>

namespace vespalib::eval::gbdt {

/**
 * Use modern optimization strategies to improve evaluation
 * performance of GBDT forests.
 *
 * Comparisons must be on the form 'feature < const' or '!(feature >=
 * const)'. The inverted form is used to signal that the true branch
 * should be selected when the feature value is missing (NaN).
 **/
class FastForest
{
protected:
    FastForest();
public:
    virtual ~FastForest();
    using UP = std::unique_ptr<FastForest>;
    class Context {
    protected:
        Context();
    public:
        virtual ~Context();
        using UP = std::unique_ptr<Context>;
    };
    static UP try_convert(const Function &fun, size_t min_fixed = 8, size_t max_fixed = 64);
    virtual vespalib::string impl_name() const = 0;
    virtual Context::UP create_context() const = 0;
    virtual double eval(Context &context, const float *params) const = 0;
    double estimate_cost_us(const std::vector<double> &params, double budget = 5.0) const;
};

}
