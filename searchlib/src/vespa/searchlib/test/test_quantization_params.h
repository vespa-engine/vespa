// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/quantization_params.h>

namespace search::test {

[[nodiscard]] constexpr attribute::QuantizationParams mse_4bit_quantization_params() noexcept {
    constexpr uint64_t seed = 0x12345678;
    constexpr auto     mode = attribute::QuantizationParams::QuantizationMode::MSE;
    return {seed, mode, 4};
}

} // namespace search::test
