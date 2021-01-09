// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/eval/typed_cells.h>
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
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator())
    {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        return _computer.squaredEuclideanDistance(&lhs_vector[0], &rhs_vector[0], sz);
    }
    double convert_threshold(double threshold) const override {
        return threshold*threshold;
    }
    double to_rawscore(double distance) const override {
        double d = sqrt(distance);
        double score = 1.0 / (1.0 + d);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
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

/**
 * Calculates angular distance between vectors
 */
template <typename FloatType>
class AngularDistance : public DistanceFunction {
public:
    AngularDistance()
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator())
    {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        auto a = &lhs_vector[0];
        auto b = &rhs_vector[0];
        double a_norm_sq = _computer.dotProduct(a, a, sz);
        double b_norm_sq = _computer.dotProduct(b, b, sz);
        double squared_norms = a_norm_sq * b_norm_sq;
        double dot_product = _computer.dotProduct(a, b, sz);
        double div = (squared_norms > 0) ? sqrt(squared_norms) : 1.0;
        double cosine_similarity = dot_product / div;
        double distance = 1.0 - cosine_similarity; // in range [0,2]
        return distance;
    }
    double convert_threshold(double threshold) const override {
        double cosine_similarity = cos(threshold);
        return 1.0 - cosine_similarity;
    }
    double to_rawscore(double distance) const override {
        double cosine_similarity = 1.0 - distance;
        // should be in in range [-1,1] but roundoff may cause problems:
        cosine_similarity = std::min(1.0, cosine_similarity);
        cosine_similarity = std::max(-1.0, cosine_similarity);
        double angle_distance = acos(cosine_similarity); // in range [0,pi]
        double score = 1.0 / (1.0 + angle_distance);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
                           double /*limit*/) const override
    {
        return calc(lhs, rhs);
    }

    const vespalib::hwaccelrated::IAccelrated & _computer;
};

/**
 * Calculates inner-product "distance" between vectors with assumed norm 1.
 * Should give same ordering as Angular distance, but is less expensive.
 */
template <typename FloatType>
class InnerProductDistance : public DistanceFunction {
public:
    InnerProductDistance()
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator())
    {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        double score = 1.0 - _computer.dotProduct(&lhs_vector[0], &rhs_vector[0], sz);
        return std::max(0.0, score);
    }
    double convert_threshold(double threshold) const override {
        return threshold;
    }
    double to_rawscore(double distance) const override {
        double score = 1.0 / (1.0 + distance);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
                           double /*limit*/) const override
    {
        return calc(lhs, rhs);
    }

    const vespalib::hwaccelrated::IAccelrated & _computer;
};

/**
 * Calculates great-circle distance between Latitude/Longitude pairs,
 * measured in degrees.  Output distance is measured in meters.
 * Uses the haversine formula directly from:
 * https://en.wikipedia.org/wiki/Haversine_formula
 **/
template <typename FloatType>
class GeoDegreesDistance : public DistanceFunction {
public:
    // in km, as defined by IUGG, see:
    // https://en.wikipedia.org/wiki/Earth_radius#Mean_radius
    static constexpr double earth_mean_radius = 6371.0088;
    static constexpr double degrees_to_radians = M_PI / 180.0;

    GeoDegreesDistance() {}
    // haversine function:
    static double hav(double angle) {
        double s = sin(0.5*angle);
        return s*s;
    }
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        assert(2 == lhs_vector.size());
        assert(2 == rhs_vector.size());
        // convert to radians:
        double lat_A = lhs_vector[0] * degrees_to_radians;
        double lat_B = rhs_vector[0] * degrees_to_radians;
        double lon_A = lhs_vector[1] * degrees_to_radians;
        double lon_B = rhs_vector[1] * degrees_to_radians;

        double lat_diff = lat_A - lat_B;
        double lon_diff = lon_A - lon_B;

        // haversines of differences:
        double hav_lat = hav(lat_diff);
        double hav_lon = hav(lon_diff);

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
    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
                           double /*limit*/) const override
    {
        return calc(lhs, rhs);
    }

};

/**
 * Calculates the Hamming distance defined as
 * "number of cells where the values are different"
 */
template <typename FloatType>
class HammingDistance : public DistanceFunction {
public:
    HammingDistance() {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        size_t sum = 0;
        for (size_t i = 0; i < sz; ++i) {
            sum += (lhs_vector[i] == rhs_vector[i]) ? 0 : 1;
        }
        return (double)sum;
    }
    double convert_threshold(double threshold) const override {
        return threshold;
    }
    double to_rawscore(double distance) const override {
        double score = 1.0 / (1.0 + distance);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
                           double limit) const override
    {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        size_t sum = 0;
        for (size_t i = 0; i < sz && sum <= limit; ++i) {
            sum += (lhs_vector[i] == rhs_vector[i]) ? 0 : 1;
        }
        return (double)sum;
    }
};


}
