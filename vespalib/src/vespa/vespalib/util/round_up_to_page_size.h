// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace vespalib {

/*
 * Return sz rounded up to a multiple of page size.
 */
size_t round_up_to_page_size(size_t sz);

}
