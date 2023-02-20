// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "independent_non_inlined_memcpy.h"

namespace vespamalloc {

// Simple memcpy replacement to avoid calling code in other dso.
// No dependencies to other libraries are allowed here.
void
independent_non_inlined_memcpy(void * dest_in, const void * src_in, size_t n) {
    char *dest = static_cast<char *>(dest_in);
    const char *src = static_cast<const char *>(src_in);
    for (size_t i(0); i < n ; i++) {
        dest[i] = src[i];
    }
}

}
