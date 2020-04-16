// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib::tensor { struct TypedCells; }

namespace search::tensor {

/**
 * Interface used to calculate the distance between two n-dimensional vectors.
 *
 * The vectors must be of same size and same type (float or double).
 * The actual implementation must know which type the vectors are.
 */
class DistanceFunction {
public:
    using UP = std::unique_ptr<DistanceFunction>;
    virtual ~DistanceFunction() {}
    virtual double calc(const vespalib::tensor::TypedCells& lhs, const vespalib::tensor::TypedCells& rhs) const = 0;
    virtual double to_rawscore(double distance) const = 0;
    virtual double calc_with_limit(const vespalib::tensor::TypedCells& lhs,
                                   const vespalib::tensor::TypedCells& rhs,
                                   double limit) const = 0;
};

}
