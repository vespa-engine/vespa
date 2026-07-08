// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib::hwaccelerated {

// FP8 lookup tables that map from the raw bitwise u8 representation of the FP8 value to
// the raw bitwise representation of its widened f32 counterpart. This is exposed as u32
// rather than f32 literals to ensure that we preserve all qNaN vs. sNaN bits verbatim.
// Use `std::bit_cast<float>` to convert to the f32 representation.
// LUTs were generated based on the output of ONNX's f8->f32 widening functions.

// FP8_E5M2 -> f32 (as u32)
extern const uint32_t fp8_e5m2_f32_bits_lut[256];

// FP8_E4M3FN -> f32 (as u32)
extern const uint32_t fp8_e4m3fn_f32_bits_lut[256];

} // namespace vespalib::hwaccelerated
