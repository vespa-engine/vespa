// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geo_degrees_distance.h"
#include "temporary_vector_store.h"

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

using vespalib::eval::TypedCells;

class BoundGeoDistance : public BoundDistanceFunction {
private:
    mutable TemporaryVectorStore<double> _tmpSpace;
    const vespalib::ConstArrayRef<double> _lh_vector;
    static GeoDegreesDistance _g_d_helper;
public:
    BoundGeoDistance(const vespalib::eval::TypedCells& lhs)
        : _tmpSpace(lhs.size),
          _lh_vector(_tmpSpace.storeLhs(lhs))
    {}
    double calc(const vespalib::eval::TypedCells& rhs) const override {
        vespalib::ConstArrayRef<double> rhs_vector = _tmpSpace.convertRhs(rhs);
        assert(2 == _lh_vector.size());
        assert(2 == rhs_vector.size());
        // convert to radians:
        double lat_A = _lh_vector[0] * GeoDegreesDistance::degrees_to_radians;
        double lat_B = rhs_vector[0] * GeoDegreesDistance::degrees_to_radians;
        double lon_A = _lh_vector[1] * GeoDegreesDistance::degrees_to_radians;
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
    double convert_threshold(double threshold) const override {
        return _g_d_helper.convert_threshold(threshold);
    }
    double to_rawscore(double distance) const override {
        return _g_d_helper.to_rawscore(distance);
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
