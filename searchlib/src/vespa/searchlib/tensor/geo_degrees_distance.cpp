// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geo_degrees_distance.h"

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

namespace {

struct CalcGeoDegrees {
    template <typename LCT, typename RCT>
    static double invoke(const vespalib::eval::TypedCells& lhs,
                         const vespalib::eval::TypedCells& rhs)
    {
        auto lhs_vector = lhs.unsafe_typify<LCT>();
        auto rhs_vector = rhs.unsafe_typify<RCT>();

        assert(2 == lhs_vector.size());
        assert(2 == rhs_vector.size());
        // convert to radians:
        double lat_A = lhs_vector[0] * GeoDegreesDistance::degrees_to_radians;
        double lat_B = rhs_vector[0] * GeoDegreesDistance::degrees_to_radians;
        double lon_A = lhs_vector[1] * GeoDegreesDistance::degrees_to_radians;
        double lon_B = rhs_vector[1] * GeoDegreesDistance::degrees_to_radians;

        double lat_diff = lat_A - lat_B;
        double lon_diff = lon_A - lon_B;
        
        // haversines of differences:
        double hav_lat = GeoDegreesDistance::hav(lat_diff);
        double hav_lon = GeoDegreesDistance::hav(lon_diff);
        
        // haversine of central angle between the two points:
        double hav_central_angle = hav_lat + cos(lat_A)*cos(lat_B)*hav_lon;
        return hav_central_angle;
    }
};

}

double
GeoDegreesDistance::calc(const vespalib::eval::TypedCells& lhs,
                      const vespalib::eval::TypedCells& rhs) const
{
    return typify_invoke<2,TypifyCellType,CalcGeoDegrees>(lhs.type, rhs.type, lhs, rhs);
}

}
