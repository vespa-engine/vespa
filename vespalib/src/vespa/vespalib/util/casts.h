// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <type_traits>
#include <utility>

namespace vespalib {

/*
 * Template wrapping casts between different variants of
 * "char *" pointers.
 *
 * This is exactly the same as the usual reinterpret_cast,
 * but restricted so the argument must be one of the
 * expected types (char *, unsigned char *, char8_t *)
 * with appropriate constness.
 *
 * Usage:
 *  unsigned char *input;
 *  char *x = char_p_cast<char>(input);
 * Author: arnej
 */
template<typename T,
         typename U,
         typename R = std::conditional_t<std::is_const_v<U>, const T *, T *>>
R char_p_cast(U* input) {
    // currently we do not want to get char8_t, may change later:
    static_assert(std::is_same<T, char>::value ||
                  std::is_same<T, unsigned char>::value);
    using DU = std::decay<U>::type;
    static_assert(std::is_same<DU, char>::value ||
                  std::is_same<DU, unsigned char>::value ||
                  std::is_same<DU, char8_t>::value);
    return reinterpret_cast<R>(input);
}

/*
 * Helper templates for char8_t[] literals; * from:
 * https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2019/p1423r3.html
 */
template<std::size_t N>
struct char8_t_string_literal {
  static constexpr inline std::size_t size = N;
  template<std::size_t... I>
  constexpr char8_t_string_literal(const char8_t (&r)[N],
                                   std::index_sequence<I...>)
    : s{r[I]...}
  {}
  constexpr char8_t_string_literal(const char8_t (&r)[N])
    : char8_t_string_literal(r, std::make_index_sequence<N>())
  {}
  char8_t s[N];
};

template<char8_t_string_literal L, std::size_t... I>
constexpr inline const char as_char_buffer[sizeof...(I)] =
  { static_cast<char>(L.s[I])... };

template<char8_t_string_literal L, std::size_t... I>
constexpr auto& make_as_char_buffer(std::index_sequence<I...>) {
  return as_char_buffer<L, I...>;
}


} // namespace


/**
 * String literal operator converting char8_t[] to char[]
 *
 * Usage example: const char *s = u8"Münich"_C;
 */
template<vespalib::char8_t_string_literal L>
constexpr auto& operator""_C() {
    return vespalib::make_as_char_buffer<L>(std::make_index_sequence<decltype(L)::size>());
}
