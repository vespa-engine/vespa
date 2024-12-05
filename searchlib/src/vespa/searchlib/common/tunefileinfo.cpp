// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tunefileinfo.hpp"

namespace search {

TuneFileRandRead
TuneFileRandRead::consider_force_memory_map(bool force_memory_map) const noexcept
{
    TuneFileRandRead result = *this;
    if (force_memory_map) {
        result.setWantMemoryMap();
    }
    return result;
}

} // namespace search
