// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bound_distance_function.h"
#include "distance_function.h"

#include <vespa/searchcommon/attribute/distance_metric.h>
#include <vespa/searchcommon/attribute/quantization_params.h>

#include <optional>

namespace search::tensor {

/**
 * API for binding the LHS of a distance calculation
 * This allows keeping global state in the factory itself, and state
 * for one particular vector in the distance function object.
 */
struct DistanceFunctionFactory {
    using TypedCells = vespalib::eval::TypedCells;
    DistanceFunctionFactory() noexcept = default;
    virtual ~DistanceFunctionFactory() = default;
    [[nodiscard]] virtual BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const = 0;
    [[nodiscard]] virtual BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const = 0;
    using UP = std::unique_ptr<DistanceFunctionFactory>;
};

/**
 * Create a distance function factory customized for the given metric
 * variant and (attribute) cell type.
 **/
[[nodiscard]] DistanceFunctionFactory::UP make_distance_function_factory(search::attribute::DistanceMetric variant,
                                                                         vespalib::eval::CellType          cell_type);

/**
 * Create and return a distance function for the given metric variant and attribute
 * cell type. Iff `quant_params` is set, returns a variant of the distance function
 * specialized to operate on quantized tensor representations.
 *
 * Note that not all distance metrics are supported for quantized vectors.
 */
[[nodiscard]] DistanceFunctionFactory::UP
make_distance_function_factory(search::attribute::DistanceMetric variant, vespalib::eval::CellType cell_type,
                               size_t                                              vector_dimensions,
                               const std::optional<attribute::QuantizationParams>& quant_params);

} // namespace search::tensor
