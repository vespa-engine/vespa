// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string_view>

namespace vespalib {

enum class MallocImpl { LibcOrUnknown, VespaMalloc, MiMalloc };

// Attempts to deduce the currently running malloc implementation based on particular
// symbols that are present (or not) in the process runtime. We can currently detect
// vespamalloc and mimalloc. If neither of these are present, either glibc malloc
// or some unknown malloc implementation is being used.
[[nodiscard]] MallocImpl detect_malloc_impl() noexcept;

[[nodiscard]] std::string_view to_string(MallocImpl mi) noexcept;

} // namespace vespalib
