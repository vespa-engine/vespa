// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/util/typify.h>
#include <cmath>

namespace search::tensor {

/**
 * Calculates great-circle distance between Latitude/Longitude pairs,
 * measured in degrees.  Output distance is measured in meters.
 * Uses the haversine formula directly from:
 * https://en.wikipedia.org/wiki/Haversine_formula
 **/
class GeoDegreesDistance : public DistanceFunction {
public:
    // in km, as defined by IUGG, see:
    // https://en.wikipedia.org/wiki/Earth_radius#Mean_radius
    static constexpr double earth_mean_radius = 6371.0088;
    static constexpr double degrees_to_radians = M_PI / 180.0;

    GeoDegreesDistance(vespalib::eval::CellType expected) : DistanceFunction(expected) {}
    // haversine function:
    static double hav(double angle) {
        double s = sin(0.5*angle);
        return s*s;
    }
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override;
    double convert_threshold(double threshold) const override {
        double half_angle = threshold / (2 * earth_mean_radius);
        double rt_hav = sin(half_angle);
        return rt_hav * rt_hav;
    }
    double to_rawscore(double distance) const override {
        double hav_diff = sqrt(distance);
        // distance in kilometers:
        double d = 2 * asin(hav_diff) * earth_mean_radius;
        // km to rawscore:
        return 1.0 / (1.0 + d);
    }
    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
                           double /*limit*/) const override
    {
        return calc(lhs, rhs);
    }
};

}
