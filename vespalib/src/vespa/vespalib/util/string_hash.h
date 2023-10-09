// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/**
 * simple string hashing function similar to the one used by Java.
 **/
double hash2d(const char *str, size_t len);
double hash2d(vespalib::stringref str);

} // namespace vespalib

