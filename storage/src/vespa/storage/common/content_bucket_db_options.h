// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace storage {

/**
 * Type-safe encapsulation of any options that can be passed from config
 * to the content node bucket database implementation.
 */
struct ContentBucketDbOptions {
    // The number of DB stripes created will be 2^n (within implementation limits)
    // TODO expose max limit here?
    uint8_t n_stripe_bits = 0;
};

}
