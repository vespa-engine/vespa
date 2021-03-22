// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "macro.h"
#include <iostream>
#include <vespa/vespalib/util/exception.h>

namespace vespalib {

VESPA_DEFINE_EXCEPTION(RequireFailedException, Exception);

constexpr void handle_require_success() {}

void throw_require_failed [[noreturn]] (const char *description, const char *file, uint32_t line);

void handle_require_failure [[noreturn]] (const char *description, const char *file, uint32_t line);

template<typename A, typename B>
void handle_require_eq_failure [[noreturn]] (const A& a, const B& b, const char *a_desc, const char *b_desc,
                                             const char *description, const char *file, uint32_t line)
{
    std::cerr << file << ":" << line << ": error: ";
    std::cerr << "expected (" << a_desc << " == " << b_desc << ")\n";
    std::cerr << "  lhs (" << a_desc << ") is: " << a << "\n";
    std::cerr << "  rhs (" << b_desc << ") is: " << b << "\n";
    throw_require_failed(description, file, line);
}

/**
 * Require a condition to be true.
 * If the requirement is not met, prints a nice message and throws
 * an exception.  Use instead of assert() or ASSERT_TRUE().
 **/
#define REQUIRE(...)                                            \
    (__VA_ARGS__) ? vespalib::handle_require_success() :        \
    vespalib::handle_require_failure(VESPA_STRINGIZE(__VA_ARGS__),     \
                                     __FILE__, __LINE__)

/**
 * Require two values to be equal.
 * If the requirement is not met, prints a nice message and throws
 * an exception.  Use instead of assert() or ASSERT_TRUE().
 * Note: both operator== and operator<< (to stream) must be implemented
 * for the value types.
 **/
#define REQUIRE_EQ(a, b)                                                \
    (a == b) ? vespalib::handle_require_success() :                     \
    vespalib::handle_require_eq_failure(a, b,                           \
                                        VESPA_STRINGIZE(a), VESPA_STRINGIZE(b), \
                                        VESPA_STRINGIZE(a) " == " VESPA_STRINGIZE(b), \
                                        __FILE__, __LINE__)

} // namespace
