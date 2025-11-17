// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bound_distance_function.h"

#include <cstddef>

namespace search::tensor {

    /**
     * Decorator that counts the number of distances computed by a BoundDistanceFunction.
     */
    class CountingBoundDistanceFunction : public BoundDistanceFunction {
    private:
        const BoundDistanceFunction &_distance_function;
        mutable std::size_t _distances_computed;

    public:
        CountingBoundDistanceFunction(const BoundDistanceFunction &distance_function) noexcept
            : _distance_function(distance_function),
              _distances_computed(0) {
        }

        ~CountingBoundDistanceFunction() override {
        }

        std::size_t distances_computed() const noexcept {
            return _distances_computed;
        }

        double calc(TypedCells rhs) const noexcept override {
            ++_distances_computed;
            return _distance_function.calc(rhs);
        }

        double calc_with_limit(TypedCells rhs, double limit) const noexcept override {
            ++_distances_computed;
            return _distance_function.calc_with_limit(rhs, limit);
        }

        double convert_threshold(double threshold) const noexcept override {
            return _distance_function.convert_threshold(threshold);
        }

        double to_rawscore(double distance) const noexcept override {
            return _distance_function.to_rawscore(distance);
        }

        double to_distance(double rawscore) const noexcept override {
            return _distance_function.to_distance(rawscore);
        }

        double min_rawscore() const noexcept override {
            return _distance_function.min_rawscore();
        }
    };

}
