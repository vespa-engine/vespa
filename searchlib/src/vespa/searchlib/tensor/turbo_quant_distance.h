// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "mips_distance_transform.h"

namespace search::tensor {

/**
 * Factory for TurboQuant distance functions.
 *
 * This implements a native quantized distance approximation that combines
 * low-bit quantized inner product with a residual-sign correction term.
 */
template <typename FloatType>
class TurboQuantDistanceFunctionFactory : public MipsDistanceFunctionFactoryBase {
public:
    using TypedCells = DistanceFunctionFactory::TypedCells;

    TurboQuantDistanceFunctionFactory() noexcept : TurboQuantDistanceFunctionFactory(false) {}
    TurboQuantDistanceFunctionFactory(bool reference_insertion_vector) noexcept
        : _reference_insertion_vector(reference_insertion_vector),
          _mse_levels(4)
    {
    }
    ~TurboQuantDistanceFunctionFactory() override = default;

    BoundDistanceFunction::UP for_query_vector(TypedCells lhs) const override;
    BoundDistanceFunction::UP for_insertion_vector(TypedCells lhs) const override;
    void save_state(vespalib::GenericHeader& header) const override;
    void load_state(const vespalib::GenericHeader& header) override;

    uint32_t mse_levels() const noexcept { return _mse_levels; }

private:
    bool _reference_insertion_vector;
    uint32_t _mse_levels;
};

}