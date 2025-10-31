// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib::hwaccelerated {

// TODO hidden visibility?

// FP8E5M2 -> f32 as u32
extern const uint32_t fp8_e5m2_f32_bits_lut[256];

// FP8E4M3FN -> f32 as u32
extern const uint32_t fp8_e4m3fn_f32_bits_lut[256];

}
