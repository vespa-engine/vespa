// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/hwaccelerated/fn_table.h>

namespace vespalib::hwaccelerated {

// Replaces the globally active vectorization function table for the lifetime
// of the object. The function table in the scope will be a composite of that
// of the table that was active upon scope construction, with the new table
// patched in on top of it. Functions tagged as suboptimal _will_ be included
// in the table.
// Upon object destruction, the old function table is restored automatically.
class ScopedFnTableOverride {
    dispatch::FnTable _original_fn_table;

public:
    explicit ScopedFnTableOverride(const dispatch::FnTable& new_sparse_table)
        : _original_fn_table(dispatch::active_fn_table()) {
        auto composite_table = dispatch::build_composite_fn_table(new_sparse_table, _original_fn_table, false);
        dispatch::thread_unsafe_update_function_dispatch_pointers(composite_table);
    }
    ~ScopedFnTableOverride() { dispatch::thread_unsafe_update_function_dispatch_pointers(_original_fn_table); }

    // Only intended for simple stack scopes, no copying/moving
    ScopedFnTableOverride(const ScopedFnTableOverride&) = delete;
    ScopedFnTableOverride& operator=(const ScopedFnTableOverride&) = delete;
    ScopedFnTableOverride(ScopedFnTableOverride&&) noexcept = delete;
    ScopedFnTableOverride& operator=(ScopedFnTableOverride&&) noexcept = delete;
};

} // namespace vespalib::hwaccelerated
