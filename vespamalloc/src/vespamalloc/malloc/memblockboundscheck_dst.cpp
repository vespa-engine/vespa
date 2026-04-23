// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespamalloc/malloc/memblockboundscheck_dst.h>
#include <vespamalloc/malloc/memblockboundscheck.hpp>

namespace vespamalloc {

template class MemBlockBoundsCheckBaseT<20, MALLOC_STACK_SAVE_LEN>;

}
