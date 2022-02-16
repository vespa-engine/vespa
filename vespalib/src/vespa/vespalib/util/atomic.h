// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>
#include <type_traits>
#include <version>

/**
 * Utility functions for single value atomic memory accesses.
 *
 * store/load_ref_* functions can be used to provide well-defined atomic
 * memory access to memory locations that aren't explicitly wrapped in std::atomic
 * objects. In this case, all potentially racing loads/stores _must_ be through
 * atomic utility functions (or atomic_ref).
 *
 * Non-ref store/load_* functions are just syntactic sugar to make code using
 * atomics more readable, but additionally adds sanity checks that all atomics
 * are always lock-free.
 */

namespace vespalib::atomic {

//
// std::atomic_ref<T> helpers
//

namespace detail {
template <typename T> struct is_std_atomic : std::false_type {};
template <typename T> struct is_std_atomic<std::atomic<T>> : std::true_type {};
template <typename T> inline constexpr bool is_std_atomic_v = is_std_atomic<T>::value;
}

// TODO can generalize atomic_ref code once no special casing is needed

template <typename T1, typename T2>
constexpr void store_ref_relaxed(T1& lhs, T2&& v) noexcept {
    static_assert(!detail::is_std_atomic_v<T1>, "atomic ref function invoked with a std::atomic, probably not intended");
#if __cpp_lib_atomic_ref
    static_assert(std::atomic_ref<T1>::is_always_lock_free);
    std::atomic_ref<T1>(lhs).store(std::forward<T2>(v), std::memory_order_relaxed);
#else
    // TODO replace with compiler intrinsic
    lhs = std::forward<T2>(v);
#endif
}

template <typename T1, typename T2>
constexpr void store_ref_release(T1& lhs, T2&& v) noexcept {
    static_assert(!detail::is_std_atomic_v<T1>, "atomic ref function invoked with a std::atomic, probably not intended");
#if __cpp_lib_atomic_ref
    static_assert(std::atomic_ref<T1>::is_always_lock_free);
    std::atomic_ref<T1>(lhs).store(std::forward<T2>(v), std::memory_order_release);
#else
    // TODO replace with compiler intrinsic
    lhs = std::forward<T2>(v);
    std::atomic_thread_fence(std::memory_order_release);
#endif
}

template <typename T1, typename T2>
constexpr void store_ref_seq_cst(T1& lhs, T2&& v) noexcept {
    static_assert(!detail::is_std_atomic_v<T1>, "atomic ref function invoked with a std::atomic, probably not intended");
#if __cpp_lib_atomic_ref
    static_assert(std::atomic_ref<T1>::is_always_lock_free);
    std::atomic_ref<T1>(lhs).store(std::forward<T2>(v), std::memory_order_seq_cst);
#else
    // TODO replace with compiler intrinsic
    lhs = std::forward<T2>(v);
    std::atomic_thread_fence(std::memory_order_seq_cst);
#endif
}

template <typename T>
[[nodiscard]] constexpr T load_ref_relaxed(const T& a) noexcept {
    static_assert(!detail::is_std_atomic_v<T>, "atomic ref function invoked with a std::atomic, probably not intended");
#if __cpp_lib_atomic_ref
    static_assert(std::atomic_ref<const T>::is_always_lock_free);
    return std::atomic_ref<const T>(a).load(std::memory_order_relaxed);
#else
    // TODO replace with compiler intrinsic
    return a;
#endif
}

template <typename T>
[[nodiscard]] constexpr T load_ref_acquire(const T& a) noexcept {
    static_assert(!detail::is_std_atomic_v<T>, "atomic ref function invoked with a std::atomic, probably not intended");
#if __cpp_lib_atomic_ref
    static_assert(std::atomic_ref<const T>::is_always_lock_free);
    return std::atomic_ref<const T>(a).load(std::memory_order_acquire);
#else
    // TODO replace with compiler intrinsic
    std::atomic_thread_fence(std::memory_order_acquire);
    return a;
#endif
}

template <typename T>
[[nodiscard]] constexpr T load_ref_seq_cst(const T& a) noexcept {
    static_assert(!detail::is_std_atomic_v<T>, "atomic ref function invoked with a std::atomic, probably not intended");
#if __cpp_lib_atomic_ref
    static_assert(std::atomic_ref<const T>::is_always_lock_free);
    return std::atomic_ref<const T>(a).load(std::memory_order_seq_cst);
#else
    // TODO replace with compiler intrinsic
    std::atomic_thread_fence(std::memory_order_seq_cst);
    return a;
#endif
}

//
// std::atomic<T> helpers
//

template <typename T1, typename T2>
constexpr void store_relaxed(std::atomic<T1>& lhs, T2&& v) noexcept {
    static_assert(std::atomic<T1>::is_always_lock_free);
    lhs.store(std::forward<T2>(v), std::memory_order_relaxed);
}

template <typename T1, typename T2>
constexpr void store_release(std::atomic<T1>& lhs, T2&& v) noexcept {
    static_assert(std::atomic<T1>::is_always_lock_free);
    lhs.store(std::forward<T2>(v), std::memory_order_release);
}

template <typename T1, typename T2>
constexpr void store_seq_cst(std::atomic<T1>& lhs, T2&& v) noexcept {
    static_assert(std::atomic<T1>::is_always_lock_free);
    lhs.store(std::forward<T2>(v), std::memory_order_seq_cst);
}

template <typename T>
[[nodiscard]] constexpr T load_relaxed(const std::atomic<T>& a) noexcept {
    static_assert(std::atomic<T>::is_always_lock_free);
    return a.load(std::memory_order_relaxed);
}

template <typename T>
[[nodiscard]] constexpr T load_acquire(const std::atomic<T>& a) noexcept {
    static_assert(std::atomic<T>::is_always_lock_free);
    return a.load(std::memory_order_acquire);
}

template <typename T>
[[nodiscard]] constexpr T load_seq_cst(const std::atomic<T>& a) noexcept {
    static_assert(std::atomic<T>::is_always_lock_free);
    return a.load(std::memory_order_seq_cst);
}

} // vespalib::atomic
