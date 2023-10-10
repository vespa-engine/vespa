// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib::eval {

// Map an address to a symbolic name.
// Intended for function pointers.

vespalib::string addr_to_symbol(const void *addr);

// Return the address of a local symbol.
// Used for testing.

const void *get_addr_of_local_test_symbol();

}
