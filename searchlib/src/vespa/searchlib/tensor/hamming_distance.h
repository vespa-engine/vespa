// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/util/typify.h>
#include <cmath>

namespace search::tensor {

/**
 * Calculates the Hamming distance defined as
 * "number of cells where the values are different"
 * or (for int8 cells, aka binary data only)
 * "number of bits that are different"
 */
class HammingDistance final : public DistanceFunction {
public:
    HammingDistance(vespalib::eval::CellType expected) : DistanceFunction(expected) {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override;
    double convert_threshold(double threshold) const override {
        return threshold;
    }
    double to_rawscore(double distance) const override {
        double score = 1.0 / (1.0 + distance);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs, double) const override;
};

}
