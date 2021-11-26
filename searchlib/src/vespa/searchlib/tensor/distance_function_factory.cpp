// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function_factory.h"
#include "distance_functions.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.distance_function_factory");

using search::attribute::DistanceMetric;
using vespalib::eval::CellType;
using vespalib::eval::ValueType;

namespace search::tensor {

DistanceFunction::UP
make_distance_function(DistanceMetric variant, CellType cell_type)
{
    switch (variant) {
    case DistanceMetric::Euclidean:
        switch (cell_type) {
        case CellType::FLOAT:  return std::make_unique<SquaredEuclideanDistanceHW<float>>();
        case CellType::DOUBLE: return std::make_unique<SquaredEuclideanDistanceHW<double>>();
        case CellType::INT8: return std::make_unique<SquaredEuclideanDistanceHW<vespalib::eval::Int8Float>>();
        default:               return std::make_unique<SquaredEuclideanDistance>(CellType::FLOAT);
        } 
    case DistanceMetric::Angular:
        switch (cell_type) {
        case CellType::FLOAT:  return std::make_unique<AngularDistanceHW<float>>();
        case CellType::DOUBLE: return std::make_unique<AngularDistanceHW<double>>();
        default:               return std::make_unique<AngularDistance>(CellType::FLOAT);
        }
    case DistanceMetric::GeoDegrees:
        return std::make_unique<GeoDegreesDistance>(CellType::DOUBLE);
    case DistanceMetric::InnerProduct:
        switch (cell_type) {
        case CellType::FLOAT:  return std::make_unique<InnerProductDistanceHW<float>>();
        case CellType::DOUBLE: return std::make_unique<InnerProductDistanceHW<double>>();
        default:               return std::make_unique<InnerProductDistance>(CellType::FLOAT);
        }
    case DistanceMetric::Hamming:
        return std::make_unique<HammingDistance>(cell_type);
    }
    // not reached:
    return DistanceFunction::UP();
}

}
