// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_metric_utils.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search::attribute {

namespace {

const vespalib::string euclidean = "euclidean";
const vespalib::string angular = "angular";
const vespalib::string geodegrees = "geodegrees";
const vespalib::string innerproduct = "innerproduct";
const vespalib::string prenormalized_angular = "prenormalized_angular";
const vespalib::string dotproduct = "dotproduct";
const vespalib::string hamming = "hamming";

}

vespalib::string
DistanceMetricUtils::to_string(DistanceMetric metric)
{
    switch (metric) {
        case DistanceMetric::Euclidean: return euclidean;
        case DistanceMetric::Angular: return angular;
        case DistanceMetric::GeoDegrees: return geodegrees;
        case DistanceMetric::InnerProduct: return innerproduct;
        case DistanceMetric::Hamming: return hamming;
        case DistanceMetric::PrenormalizedAngular: return prenormalized_angular;
        case DistanceMetric::Dotproduct: return dotproduct;
    }
    throw vespalib::IllegalArgumentException("Unknown distance metric " + std::to_string(static_cast<int>(metric)));
}

DistanceMetric
DistanceMetricUtils::to_distance_metric(const vespalib::string& metric)
{
    if (metric == euclidean) {
        return DistanceMetric::Euclidean;
    } else if (metric == angular) {
        return DistanceMetric::Angular;
    } else if (metric == geodegrees) {
        return DistanceMetric::GeoDegrees;
    } else if (metric == innerproduct) {
        return DistanceMetric::InnerProduct;
    } else if (metric == prenormalized_angular) {
        return DistanceMetric::PrenormalizedAngular;
    } else if (metric == dotproduct) {
        return DistanceMetric::Dotproduct;
    } else if (metric == hamming) {
        return DistanceMetric::Hamming;
    } else {
        throw vespalib::IllegalStateException("Unknown distance metric '" + metric + "'");
    }
}

}
