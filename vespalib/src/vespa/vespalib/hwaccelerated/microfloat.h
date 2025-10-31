// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::hwaccelerated {

enum class MicroFloatKind {
    FP8_E4M2FN,
    FP8_E5M2,
    FP4_E2M1 // Contains 2 FP4s; #0 in LSB nibble, #1 in MSB nibble
    // TODO OCP MX prefix? What about NVFP4? Where do scaling factors live?
    //  does it even make sense to have FP4 without scaling factors...?
};

} // vespalib::hwaccelerated
