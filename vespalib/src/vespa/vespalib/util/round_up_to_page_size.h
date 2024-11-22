// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace vespalib {

/*
 * Return offset rounded down to a page boundary.
 */
uint64_t round_down_to_page_boundary(uint64_t offset);

/*
 * Return sz rounded up to a multiple of page size.
 */
size_t round_up_to_page_size(size_t sz);

}
