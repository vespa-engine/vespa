// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "round_up_to_page_size.h"
#include <unistd.h>

namespace vespalib {

namespace {

const size_t page_size = getpagesize();

}

uint64_t round_down_to_page_boundary(uint64_t offset)
{
    return (offset & ~static_cast<uint64_t>(page_size - 1));
}

size_t round_up_to_page_size(size_t size)
{
    return ((size + (page_size - 1)) & ~(page_size - 1));
}

}
