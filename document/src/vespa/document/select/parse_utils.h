// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdint.h>
#include <stddef.h>

namespace document::select::util {

// Fast, locale-independent numeric parse helpers for Flex lexing.

// For all parse_* functions, returns true if parsing is successful. False otherwise.
// Value of `out` is undefined if return value is false.
bool parse_hex_i64(const char* str, size_t len, int64_t& out);
bool parse_i64(const char* str, size_t len, int64_t& out);
bool parse_double(const char* str, size_t len, double& out);

}