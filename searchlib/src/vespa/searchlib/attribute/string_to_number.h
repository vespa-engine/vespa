// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string_view>

namespace search {

/**
 * Converts a string to a number of type T.
 * Throws vespalib::IllegalArgumentException if the conversion fails.
 */
template <typename T>
T string_to_number(std::string_view str);

}
