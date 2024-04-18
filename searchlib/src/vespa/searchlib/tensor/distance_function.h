// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::tensor {

class DistanceConverter {
public:
    virtual ~DistanceConverter() = default;

    /**
     * Convert threshold (external distance units) to internal units.
     */
    virtual double convert_threshold(double threshold) const noexcept = 0;

    /**
     * Convert internal distance to rawscore (also used as closeness).
     */
    virtual double to_rawscore(double distance) const noexcept = 0;

    /**
     * Convert rawscore to external distance.
     * Override this when the rawscore is NOT defined as (1.0 / (1.0 + external_distance)).
     */
    virtual double to_distance(double rawscore) const noexcept {
        return (1.0 / rawscore) - 1.0;
    }

    /**
     * The minimum rawscore (also used as closeness) that this distance function can return.
     */
    virtual double min_rawscore() const noexcept {
        return 0.0;
    }
};

}
