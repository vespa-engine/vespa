// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace {

unsigned int mix_bits(unsigned int x) {
        x ^= (x >> 16);
        x *= 0x21F0AAAD;
        x ^= (x >> 15);
        x *= 0xD35A2D97;
        x ^= (x >> 15);
        return x;
}

unsigned int mix_hash(unsigned int key, unsigned int bias, unsigned int size) {
        return mix_bits(key + bias) % size;
}

} // namespace <unnamed>
} // namespace vespalib
