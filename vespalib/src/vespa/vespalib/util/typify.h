// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "traits.h"
#include <cstddef>
#include <utility>

namespace vespalib {

//-----------------------------------------------------------------------------

/**
 * Typification result for values resolving into actual types. Using
 * this exact template is not required, but the result type must have
 * the name 'type' for the auto-unwrapping performed by typify_invoke
 * to work.
 **/
template <typename T> struct TypifyResultType {
    using type = T;
};

/**
 * Typification result for values resolving into non-types. Using this
 * exact template is not required, but is supplied for
 * convenience. The resolved compile-time value should be called
 * 'value' for consistency across typifiers.
 **/
template <typename T, T VALUE> struct TypifyResultValue {
    static constexpr T value = VALUE;
};

/**
 * Typification result for values resolving into simple templates
 * (templated on one type). Using this exact template is not required,
 * but is supplied for convenience and as example. The resolved
 * template should be called 'templ' for consistency across typifiers.
 **/
template <template<typename> typename TT> struct TypifyResultSimpleTemplate {
    template <typename T> using templ = TT<T>;
};

/**
 * A Typifier is able to take a run-time value and resolve it into a
 * type, non-type constant value or a template. The resolve result is
 * passed to the specified function in the form of a thin result
 * wrapper.
 **/
struct TypifyBool {
    template <bool VALUE> using Result = TypifyResultValue<bool, VALUE>;
    template <typename F> static decltype(auto) resolve(bool value, F &&f) {
        if (value) {
            return f(Result<true>());
        } else {
            return f(Result<false>());
        }
    }
};

//-----------------------------------------------------------------------------

/**
 * Template used to combine individual typifiers into a typifier able
 * to resolve multiple types.
 **/
template <typename ...Ts> struct TypifyValue : Ts... { using Ts::resolve...; };

//-----------------------------------------------------------------------------

template <size_t N, typename Typifier, typename Target, typename ...Rs> struct TypifyInvokeImpl {
    static decltype(auto) select() {
        static_assert(sizeof...(Rs) == N);
        return Target::template invoke<Rs...>();
    }
    template <typename T, typename ...Args> static decltype(auto) select(T &&value, Args &&...args) {
        if constexpr (N == sizeof...(Rs)) {
            return Target::template invoke<Rs...>(std::forward<T>(value), std::forward<Args>(args)...);
        } else {
            return Typifier::resolve(value, [&](auto t)->decltype(auto)
                                     {
                                         using X = decltype(t);
                                         if constexpr (has_type_type<X>) {
                                             return TypifyInvokeImpl<N, Typifier, Target, Rs..., typename X::type>::select(std::forward<Args>(args)...);
                                         } else {
                                             return TypifyInvokeImpl<N, Typifier, Target, Rs..., X>::select(std::forward<Args>(args)...);
                                         }
                                     });
        }
    }
};

/**
 * Typify the N first parameters using 'Typifier' (typically an
 * instantiation of the TypifyValue template) and forward the
 * remaining parameters to the Target::invoke template function with
 * the typification results from the N first parameters as template
 * parameters. Note that typification results that are types are
 * unwrapped before being used as template parameters while
 * typification results that are non-types or templates are kept in
 * their wrappers when passed as template parameters. Please refer to
 * the unit test for examples.
 **/
template <size_t N, typename Typifier, typename Target, typename ...Args> decltype(auto) typify_invoke(Args && ...args) {
    static_assert(N > 0);
    return TypifyInvokeImpl<N,Typifier,Target>::select(std::forward<Args>(args)...);
}

//-----------------------------------------------------------------------------

}
