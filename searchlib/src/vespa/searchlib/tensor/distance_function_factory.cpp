// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function_factory.h"
#include "distance_functions.h"
#include "mips_distance_transform.h"

using search::attribute::DistanceMetric;
using vespalib::eval::CellType;
using vespalib::eval::Int8Float;

namespace search::tensor {

std::unique_ptr<DistanceFunctionFactory>
make_distance_function_factory(DistanceMetric variant, CellType cell_type)
{
    switch (variant) {
        case DistanceMetric::Angular:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<AngularDistanceFunctionFactory<double>>();
                case CellType::INT8:   return std::make_unique<AngularDistanceFunctionFactory<Int8Float>>();
                default:               return std::make_unique<AngularDistanceFunctionFactory<float>>();
            }
        case DistanceMetric::Euclidean:
            switch (cell_type) {
                case CellType::DOUBLE:   return std::make_unique<EuclideanDistanceFunctionFactory<double>>();
                case CellType::INT8:     return std::make_unique<EuclideanDistanceFunctionFactory<Int8Float>>();
                case CellType::BFLOAT16: return std::make_unique<EuclideanDistanceFunctionFactory<vespalib::BFloat16>>();
                default:                 return std::make_unique<EuclideanDistanceFunctionFactory<float>>();
            }
        case DistanceMetric::InnerProduct:
        case DistanceMetric::PrenormalizedAngular:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<double>>();
                case CellType::INT8:   return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<Int8Float>>();
                default:               return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<float>>();
            }
        case DistanceMetric::Dotproduct:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<MipsDistanceFunctionFactory<double>>();
                case CellType::INT8:   return std::make_unique<MipsDistanceFunctionFactory<Int8Float>>();
                default:               return std::make_unique<MipsDistanceFunctionFactory<float>>();
            }
        case DistanceMetric::GeoDegrees:
            return std::make_unique<GeoDistanceFunctionFactory>();
        case DistanceMetric::Hamming:
            switch (cell_type) {
                case CellType::DOUBLE: return std::make_unique<HammingDistanceFunctionFactory<double>>();
                case CellType::INT8:   return std::make_unique<HammingDistanceFunctionFactory<Int8Float>>();
                default:               return std::make_unique<HammingDistanceFunctionFactory<float>>();
            }
    }
    // not reached:
    return {};
}

}
