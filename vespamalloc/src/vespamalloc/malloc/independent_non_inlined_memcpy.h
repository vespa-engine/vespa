// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>

namespace vespamalloc {

// Simple memcpy replacement to avoid calling code in other dso.
void independent_non_inlined_memcpy(void *dest_in, const void *src_in, size_t n);

}
