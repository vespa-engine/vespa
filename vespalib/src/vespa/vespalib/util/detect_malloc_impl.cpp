// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "detect_malloc_impl.h"

#include <cstdio>
#include <cstdlib>

extern "C" {

// Weakly resolved symbols that will be nullptr if they fail to resolve. Used to
// detect the presence of a particular malloc implementation.
// Vespamalloc:
void vespamalloc_dump_info(FILE* out_file) __attribute__((weak));
// MiMalloc:
// From https://microsoft.github.io/mimalloc/group__extended.html:
using mi_output_fun = void(const char* msg, void* aux_arg);
void mi_stats_print_out(mi_output_fun* out, void* aux_arg) __attribute__((weak));
}

namespace vespalib {

[[nodiscard]]
MallocImpl detect_malloc_impl() noexcept {
    if (vespamalloc_dump_info != nullptr) {
        return MallocImpl::VespaMalloc;
    }
    if (mi_stats_print_out != nullptr) {
        return MallocImpl::MiMalloc;
    }
    return MallocImpl::LibcOrUnknown;
}

[[nodiscard]]
std::string_view to_string(MallocImpl mi) noexcept {
    switch (mi) {
    case MallocImpl::VespaMalloc:
        return "vespamalloc";
    case MallocImpl::MiMalloc:
        return "mimalloc";
    case MallocImpl::LibcOrUnknown:
        return "libc_or_unknown";
    }
    abort();
}

} // namespace vespalib
