// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::attribute {

/**
 * Parameters for tensor attributes storing quantized dense subspaces.
 *
 * None of these parameters can be changed live on an existing tensor attribute;
 * it must be rebuilt with updated parameters from the doc store source-of-truth.
 * This also transitively applies to structures that depend on the attribute,
 * such as HNSW indexes.
 */
class QuantizationParams {
public:
    enum class QuantizationMode { MSE, InnerProduct };

private:
    uint64_t         _seed;
    QuantizationMode _quantization_mode;
    uint8_t          _bits;

public:
    constexpr QuantizationParams(uint64_t seed_, QuantizationMode quantization_mode_, uint8_t bits_) noexcept
        : _seed(seed_), _quantization_mode(quantization_mode_), _bits(bits_) {}

    [[nodiscard]] constexpr uint64_t seed() const noexcept { return _seed; }
    [[nodiscard]] constexpr QuantizationMode quantization_mode() const noexcept { return _quantization_mode; }
    [[nodiscard]] constexpr uint8_t bits() const noexcept { return _bits; }

    constexpr bool operator==(const QuantizationParams&) const noexcept = default;
};

} // namespace search::attribute
