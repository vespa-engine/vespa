// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "round_up_to_page_size.h"
#include <unistd.h>

namespace vespalib {

namespace {

const size_t page_size = getpagesize();

}

size_t round_up_to_page_size(size_t size)
{
    return ((size + (page_size - 1)) & ~(page_size - 1));
}

}
