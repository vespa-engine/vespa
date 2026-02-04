// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespamalloc/malloc/memblock.h>
#include <vespamalloc/malloc/stat.h>
#include <vespamalloc/malloc/threadpool.hpp>

namespace vespamalloc {

template class ThreadPoolT<MemBlock, NoStat>;

}
