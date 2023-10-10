// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "macro.h"
#include "approx.h"
#include "classname.h"
#include <iostream>
#include <type_traits>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/objects/hexdump.h>

namespace vespalib {

namespace require_impl {

//-----------------------------------------------------------------------------

template<typename A, typename B>
using comparability = decltype(std::declval<const A &>() == std::declval<const B &>());

template<typename A, typename B, class = void>
struct are_comparable : std::false_type {};

template<typename A, typename B>
struct are_comparable<A,B,std::void_t<comparability<A,B>>> : std::true_type{};

//-----------------------------------------------------------------------------

template<typename S, typename V>
using streamability = decltype(std::declval<S &>() << std::declval<const V &>());

template<typename S, typename V, class = void>
struct is_streamable : std::false_type {};

template<typename S, typename V>
struct is_streamable<S,V,std::void_t<streamability<S,V>>> : std::true_type{};

//-----------------------------------------------------------------------------

// memcmp is not constexpr, so we need an alternative
constexpr bool mem_eq(const char *a, const char *b, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        if (a[i] != b[i]) {
            return false;
        }
    }
    return true;
}

template <class A, class B>
constexpr bool eq(const A &a, const B &b) {
    if constexpr (are_comparable<A,B>()) {
        return (a == b);
    } else if constexpr (std::is_same_v<A,B> &&
                         std::has_unique_object_representations_v<A>)
    {
        static_assert(sizeof(A) == sizeof(B));
        return mem_eq(reinterpret_cast<const char *>(std::addressof(a)),
                      reinterpret_cast<const char *>(std::addressof(b)),
                      sizeof(A));
    } else {
        static_assert(are_comparable<A,B>(), "values are not comparable");
        return false;
    }
}

constexpr bool eq(double a, double b) { return approx_equal(a, b); }

//-----------------------------------------------------------------------------

template <typename S, typename V>
void print(S &os, const V &value) {
    if constexpr (is_streamable<S,V>()) {
        os << value;
    } else if constexpr (std::has_unique_object_representations_v<V>) {
        os << "(" << getClassName<V>() << ") " << HexDump(std::addressof(value), sizeof(V));
    } else {
        os << "not printable (type: " << getClassName<V>() << ")";
    }
}

//-----------------------------------------------------------------------------

} // namespace require_impl

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
    std::cerr << "  lhs (" << a_desc << ") is: ";
    require_impl::print(std::cerr, a);
    std::cerr << "\n";
    std::cerr << "  rhs (" << b_desc << ") is: ";
    require_impl::print(std::cerr, b);
    std::cerr << "\n";
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
    vespalib::require_impl::eq(a, b) ? vespalib::handle_require_success() : \
    vespalib::handle_require_eq_failure(a, b,                           \
                                        VESPA_STRINGIZE(a), VESPA_STRINGIZE(b), \
                                        VESPA_STRINGIZE(a) " == " VESPA_STRINGIZE(b), \
                                        __FILE__, __LINE__)

/**
 * Signal the failure of some requirement with a message.
 * Can be used instead of abort()
 **/
#define REQUIRE_FAILED(msg) \
    vespalib::handle_require_failure(msg, __FILE__, __LINE__)

} // namespace
