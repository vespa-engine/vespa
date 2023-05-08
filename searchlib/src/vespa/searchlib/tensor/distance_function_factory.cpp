// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function_factory.h"
#include "distance_functions.h"
#include "mips_distance_transform.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.distance_function_factory");

using search::attribute::DistanceMetric;
using vespalib::eval::CellType;
using vespalib::eval::ValueType;

namespace search::tensor {

std::unique_ptr<DistanceFunctionFactory>
make_distance_function_factory(search::attribute::DistanceMetric variant,
                               vespalib::eval::CellType cell_type)
{
    switch (variant) {
        case DistanceMetric::Angular:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<AngularDistanceFunctionFactory<double>>();
                default:               return std::make_unique<AngularDistanceFunctionFactory<float>>();
            }
        case DistanceMetric::Euclidean:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<EuclideanDistanceFunctionFactory<double>>();
                case CellType::INT8:   return std::make_unique<EuclideanDistanceFunctionFactory<vespalib::eval::Int8Float>>();
                case CellType::BFLOAT16: return std::make_unique<EuclideanDistanceFunctionFactory<vespalib::BFloat16>>();
                default:               return std::make_unique<EuclideanDistanceFunctionFactory<float>>();
            }
        case DistanceMetric::InnerProduct:
        case DistanceMetric::PrenormalizedAngular:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<double>>();
                default:               return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<float>>();
            }
        case DistanceMetric::Dotproduct:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<MipsDistanceFunctionFactory<double>>();
                case CellType::INT8:   return std::make_unique<MipsDistanceFunctionFactory<vespalib::eval::Int8Float>>();
                default:               return std::make_unique<MipsDistanceFunctionFactory<float>>();
            }
        case DistanceMetric::GeoDegrees:
            return std::make_unique<GeoDistanceFunctionFactory>();
        case DistanceMetric::Hamming:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<HammingDistanceFunctionFactory<double>>();
                case CellType::INT8:   return std::make_unique<HammingDistanceFunctionFactory<vespalib::eval::Int8Float>>();
                default:               return std::make_unique<HammingDistanceFunctionFactory<float>>();
            }
    }
    // not reached:
    return {};
}

}
