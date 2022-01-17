// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cmath>

namespace search::common {

/**
 * An immutable struct for a (geo) location point,
 * with methods for computing Great Circle Distance
 * using the haversine formula
 **/
struct GeoGcd
{
    GeoGcd(double lat, double lng);

    // haversine function:
    static constexpr double haversine(double angle) {
        double s = sin(0.5*angle);
        return s*s;
    }

    double km_great_circle_distance(double lat, double lng) const;
private:
    double _latitude_radians;
    double _longitude_radians;
};

} // namespace
