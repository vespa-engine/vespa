// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function_factory.h"
#include "distance_functions.h"

using search::attribute::DistanceMetric;
using vespalib::eval::CellType;
using vespalib::eval::ValueType;

namespace search::tensor {

DistanceFunction::UP
make_distance_function(DistanceMetric variant, CellType cell_type)
{
    switch (variant) {
        case DistanceMetric::Euclidean:
            if (cell_type == CellType::FLOAT) {
                return std::make_unique<SquaredEuclideanDistance<float>>();
            } else {
                return std::make_unique<SquaredEuclideanDistance<double>>();
            }
            break;
        case DistanceMetric::Angular:
            if (cell_type == CellType::FLOAT) {
                return std::make_unique<AngularDistance<float>>();
            } else {
                return std::make_unique<AngularDistance<double>>();
            }
            break;
        case DistanceMetric::GeoDegrees:
            if (cell_type == CellType::FLOAT) {
                return std::make_unique<GeoDegreesDistance<float>>();
            } else {
                return std::make_unique<GeoDegreesDistance<double>>();
            }
            break;
        case DistanceMetric::InnerProduct:
            if (cell_type == CellType::FLOAT) {
                return std::make_unique<InnerProductDistance<float>>();
            } else {
                return std::make_unique<InnerProductDistance<double>>();
            }
            break;
        case DistanceMetric::Hamming:
            if (cell_type == CellType::FLOAT) {
                return std::make_unique<HammingDistance<float>>();
            } else {
                return std::make_unique<HammingDistance<double>>();
            }
            break;
    }
    // not reached:
    return DistanceFunction::UP();
}

}
