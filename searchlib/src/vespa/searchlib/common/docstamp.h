// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search {

// since everything is real-time, the docstamp does no longer change
// as before. The value 0 still means invalid , and the
// value 42 was selected randomly to reflect a valid value. Defined
// here for a single source of truth.

struct DocStamp {
    static uint32_t good() { return 42; }
    static uint32_t bad() { return 0; }
};

} // namespace search

