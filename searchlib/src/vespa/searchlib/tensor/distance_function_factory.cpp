// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function_factory.h"

#include "distance_functions.h"
#include "mips_distance_transform.h"

using search::attribute::DistanceMetric;
using search::attribute::QuantizationParams;
using vespalib::eval::CellType;
using vespalib::eval::Int8Float;

namespace search::tensor {

std::unique_ptr<DistanceFunctionFactory> make_distance_function_factory(DistanceMetric variant, CellType cell_type) {
    switch (variant) {
    case DistanceMetric::Angular:
        switch (cell_type) {
        case CellType::DOUBLE:
            return std::make_unique<AngularDistanceFunctionFactory<double>>(true);
        case CellType::INT8:
            return std::make_unique<AngularDistanceFunctionFactory<Int8Float>>(true);
        case CellType::BFLOAT16:
            return std::make_unique<AngularDistanceFunctionFactory<vespalib::BFloat16>>(true);
        case CellType::FLOAT:
            return std::make_unique<AngularDistanceFunctionFactory<float>>(true);
        default:
            return std::make_unique<AngularDistanceFunctionFactory<float>>();
        }
    case DistanceMetric::Euclidean:
        switch (cell_type) {
        case CellType::DOUBLE:
            return std::make_unique<EuclideanDistanceFunctionFactory<double>>(true);
        case CellType::INT8:
            return std::make_unique<EuclideanDistanceFunctionFactory<Int8Float>>(true);
        case CellType::BFLOAT16:
            return std::make_unique<EuclideanDistanceFunctionFactory<vespalib::BFloat16>>(true);
        case CellType::FLOAT:
            return std::make_unique<EuclideanDistanceFunctionFactory<float>>(true);
        default:
            return std::make_unique<EuclideanDistanceFunctionFactory<float>>();
        }
    case DistanceMetric::InnerProduct:
    case DistanceMetric::PrenormalizedAngular:
        switch (cell_type) {
        case CellType::DOUBLE:
            return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<double>>(true);
        case CellType::INT8:
            return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<Int8Float>>(true);
        case CellType::BFLOAT16:
            return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<vespalib::BFloat16>>(true);
        case CellType::FLOAT:
            return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<float>>(true);
        default:
            return std::make_unique<PrenormalizedAngularDistanceFunctionFactory<float>>();
        }
    case DistanceMetric::Dotproduct:
        switch (cell_type) {
        case CellType::DOUBLE:
            return std::make_unique<MipsDistanceFunctionFactory<double>>(true);
        case CellType::INT8:
            return std::make_unique<MipsDistanceFunctionFactory<Int8Float>>(true);
        case CellType::BFLOAT16:
            return std::make_unique<MipsDistanceFunctionFactory<vespalib::BFloat16>>(true);
        case CellType::FLOAT:
            return std::make_unique<MipsDistanceFunctionFactory<float>>(true);
        default:
            return std::make_unique<MipsDistanceFunctionFactory<float>>();
        }
    case DistanceMetric::GeoDegrees:
        return std::make_unique<GeoDistanceFunctionFactory>();
    case DistanceMetric::Hamming:
        switch (cell_type) {
        case CellType::DOUBLE:
            return std::make_unique<HammingDistanceFunctionFactory<double>>(true);
        case CellType::INT8:
            return std::make_unique<HammingDistanceFunctionFactory<Int8Float>>(true);
        case CellType::BFLOAT16:
            return std::make_unique<HammingDistanceFunctionFactory<vespalib::BFloat16>>(true);
        case CellType::FLOAT:
            return std::make_unique<HammingDistanceFunctionFactory<float>>(true);
        default:
            return std::make_unique<HammingDistanceFunctionFactory<float>>();
        }
    }
    // Not reached:
    return {};
}

namespace {

DistanceFunctionFactory::UP make_quantized_distance_function_factory(DistanceMetric variant, size_t vector_dimensions,
                                                                     const QuantizationParams& quant_params) {
    // The cell type does not matter for quantized distance functions, as vectors are
    // internally treated either as opaque quantized byte (well, Int8Float to be pedantic)
    // vectors or float32 vectors.
    switch (variant) {
    case DistanceMetric::Angular:
        return std::make_unique<QuantizedAngularDistanceFunctionFactory>(vector_dimensions, quant_params.bits(),
                                                                         quant_params.seed());
    case DistanceMetric::Euclidean:
        return std::make_unique<QuantizedEuclideanDistanceFunctionFactory>(vector_dimensions, quant_params.bits(),
                                                                           quant_params.seed());
    case DistanceMetric::InnerProduct:
    case DistanceMetric::PrenormalizedAngular:
        return std::make_unique<QuantizedPrenormalizedAngularDistanceFunctionFactory>(
            vector_dimensions, quant_params.bits(), quant_params.seed());
    case DistanceMetric::Dotproduct:
        return std::make_unique<QuantizedMipsDistanceFunctionFactory>(vector_dimensions, quant_params.bits(),
                                                                      quant_params.seed());
    default:
        // Incompatible distance functions should not be allowed to be deployed.
        abort();
    }
}

} // namespace

DistanceFunctionFactory::UP make_distance_function_factory(DistanceMetric variant, CellType cell_type,
                                                           size_t                                   vector_dimensions,
                                                           const std::optional<QuantizationParams>& quant_params) {
    if (!quant_params) {
        return make_distance_function_factory(variant, cell_type);
    } else {
        return make_quantized_distance_function_factory(variant, vector_dimensions, *quant_params);
    }
}

} // namespace search::tensor
