// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geo_degrees_distance.h"
#include "temporary_vector_store.h"

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

/**
 * Calculates great-circle distance between Latitude/Longitude pairs,
 * measured in degrees.  Output distance is measured in kilometers.
 * Uses the haversine formula directly from:
 * https://en.wikipedia.org/wiki/Haversine_formula
 **/
class BoundGeoDistance : public BoundDistanceFunction {
private:
    mutable TemporaryVectorStore<double> _tmpSpace;
    const vespalib::ConstArrayRef<double> _lh_vector;
public:
    // in km, as defined by IUGG, see:
    // https://en.wikipedia.org/wiki/Earth_radius#Mean_radius
    static constexpr double earth_mean_radius = 6371.0088;
    static constexpr double degrees_to_radians = M_PI / 180.0;

    // haversine function:
    static double haversine(double angle) {
        double s = sin(0.5*angle);
        return s*s;
    }

    BoundGeoDistance(const vespalib::eval::TypedCells& lhs)
        : _tmpSpace(lhs.size),
          _lh_vector(_tmpSpace.storeLhs(lhs))
    {}
    double calc(const vespalib::eval::TypedCells& rhs) const override {
        vespalib::ConstArrayRef<double> rhs_vector = _tmpSpace.convertRhs(rhs);
        assert(2 == _lh_vector.size());
        assert(2 == rhs_vector.size());
        // convert to radians:
        double lat_A = _lh_vector[0] * degrees_to_radians;
        double lat_B = rhs_vector[0] * degrees_to_radians;
        double lon_A = _lh_vector[1] * degrees_to_radians;
        double lon_B = rhs_vector[1] * degrees_to_radians;

        double lat_diff = lat_A - lat_B;
        double lon_diff = lon_A - lon_B;

        // haversines of differences:
        double hav_lat = haversine(lat_diff);
        double hav_lon = haversine(lon_diff);

        // haversine of central angle between the two points:
        double hav_central_angle = hav_lat + cos(lat_A)*cos(lat_B)*hav_lon;
        return hav_central_angle;
    }
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
    double calc_with_limit(const vespalib::eval::TypedCells& rhs, double) const override {
        return calc(rhs);
    }
};

BoundDistanceFunction::UP
GeoDistanceFunctionFactory::for_query_vector(const vespalib::eval::TypedCells& lhs) {
    return std::make_unique<BoundGeoDistance>(lhs);
}

BoundDistanceFunction::UP
GeoDistanceFunctionFactory::for_insertion_vector(const vespalib::eval::TypedCells& lhs) {
    return std::make_unique<BoundGeoDistance>(lhs);
}

}
