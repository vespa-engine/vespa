// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/**
 * simple string hashing function similar to the one used by Java.
 **/
uint32_t hash_code(const char *str, size_t len);
uint32_t hash_code(vespalib::stringref str);

} // namespace vespalib

