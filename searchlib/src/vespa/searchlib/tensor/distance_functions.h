// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <cmath>

namespace search::tensor {

/**
 * Calculates the square of the standard Euclidean distance.
 * Will use instruction optimal for the cpu it is running on.
 */
template <typename FloatType>
class SquaredEuclideanDistance : public DistanceFunction {
public:
    SquaredEuclideanDistance()
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelrator())
    {}
    double calc(const vespalib::tensor::TypedCells& lhs, const vespalib::tensor::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        return _computer.squaredEuclideanDistance(&lhs_vector[0], &rhs_vector[0], sz);
    }
    double to_rawscore(double distance) const override {
        double d = sqrt(distance);
        double score = 1.0 / (1.0 + d);
        return score;
    }
    double calc_with_limit(const vespalib::tensor::TypedCells& lhs,
                           const vespalib::tensor::TypedCells& rhs,
                           double limit) const override
    {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        double sum = 0.0;
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        for (size_t i = 0; i < sz && sum <= limit; ++i) {
            double diff = lhs_vector[i] - rhs_vector[i];
            sum += diff*diff;
        }
        return sum;
    }

    const vespalib::hwaccelrated::IAccelrated & _computer;
};

template class SquaredEuclideanDistance<float>;
template class SquaredEuclideanDistance<double>;

/**
 * Calculates angular distance between vectors with assumed norm 1.
 */
template <typename FloatType>
class AngularDistance : public DistanceFunction {
public:
    AngularDistance()
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelrator())
    {}
    double calc(const vespalib::tensor::TypedCells& lhs, const vespalib::tensor::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        return 1.0 - _computer.dotProduct(&lhs_vector[0], &rhs_vector[0], sz);
    }
    double to_rawscore(double distance) const override {
        double score = 1.0 / (1.0 + distance);
        return score;
    }
    double calc_with_limit(const vespalib::tensor::TypedCells& lhs,
                           const vespalib::tensor::TypedCells& rhs,
                           double /*limit*/) const override
    {
        return calc(lhs, rhs);
    }

    const vespalib::hwaccelrated::IAccelrated & _computer;
};

template class AngularDistance<float>;
template class AngularDistance<double>;

/**
 * Calculates great-circle distance between Latitude/Longitude pairs,
 * measured in degrees
 **/
template <typename FloatType>
class GeoDegreesDistance : public DistanceFunction {
public:
    GeoDegreesDistance() {}
    double calc(const vespalib::tensor::TypedCells& lhs, const vespalib::tensor::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        assert(2 == lhs_vector.size());
        assert(2 == rhs_vector.size());
        // convert to radians:
        double lat_A = lhs_vector[0] * M_PI / 180.0;
        double lat_B = rhs_vector[0] * M_PI / 180.0;
        double lon_A = lhs_vector[1] * M_PI / 180.0;
        double lon_B = rhs_vector[1] * M_PI / 180.0;

        double lat_diff = lat_A - lat_B;
        double lon_diff = lon_A - lon_B;

        // sines of half of differences:
        double sin_half_lat = sin(0.5 * lat_diff);
        double sin_half_lon = sin(0.5 * lon_diff);

        // haversines of differences:
        double hav_lat = sin_half_lat*sin_half_lat;
        double hav_lon = sin_half_lon*sin_half_lon;

        // haversine of central angle between the two points:
        double hav_central_angle = hav_lat + cos(lat_A)*cos(lat_B)*hav_lon;
        return hav_central_angle;
    }
    double to_rawscore(double distance) const override {
        double hav_diff = sqrt(distance);
        // distance in meters:
        double d = 2 * asin(hav_diff) * 6371008.8; // Earth mean radius
        return 1.0 / (1.0 + d);
    }
    double calc_with_limit(const vespalib::tensor::TypedCells& lhs,
                           const vespalib::tensor::TypedCells& rhs,
                           double /*limit*/) const override
    {
        return calc(lhs, rhs);
    }

};

template class GeoDegreesDistance<float>;
template class GeoDegreesDistance<double>;

}
