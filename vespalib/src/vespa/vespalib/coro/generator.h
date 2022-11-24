// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <concepts>
#include <coroutine>
#include <exception>
#include <utility>
#include <cstddef>
#include <iterator>

namespace vespalib::coro {

/**
 * coroutine return type
 *
 * The coroutine is lazy (will suspend in initial_suspend) and
 * destroyed from the outside (will suspend in final_suspend). A
 * generator may produce any number of results using co_yield, but
 * cannot use co_await (it must be synchronous). The values produced
 * by the generator is accessed by using the generator as an
 * input_range. This kind of generator is not recursive (it cannot
 * yield other generators of the same type directly). This is done to
 * make it easier for compilers to perform HALO, code inlining and
 * even constant folding.
 **/
template <typename T, typename ValueType = std::remove_cvref<T>>
class [[nodiscard]] Generator {
public:
    using value_type = ValueType;
    using Pointer = std::add_pointer_t<T>;

    class promise_type;
    using Handle = std::coroutine_handle<promise_type>;

    class promise_type {
    private:
        Pointer _ptr;

    public:
        promise_type(promise_type &&) = delete;
        promise_type(const promise_type &) = delete;
        promise_type() noexcept : _ptr(nullptr) {}
        Generator<T> get_return_object() { return Generator(Handle::from_promise(*this)); }
        std::suspend_always initial_suspend() noexcept { return {}; }
        std::suspend_always final_suspend() noexcept { return {}; }
        std::suspend_always yield_value(T &&value) noexcept {
            _ptr = &value;
            return {};
        }
        auto yield_value(const T &value)
            noexcept(std::is_nothrow_constructible_v<T, const T &>)
            requires(!std::is_reference_v<T> && std::copy_constructible<T>)
        {
            struct awaiter : std::suspend_always {
                awaiter(const T &value_in, Pointer &ptr)
                  noexcept(std::is_nothrow_constructible_v<T, const T &>)
                  : value_cpy(value_in)
                {
                    ptr = std::addressof(value_cpy);
                }
                awaiter(awaiter&&) = delete;
                awaiter(const awaiter&) = delete;
                T value_cpy;
            };
            return awaiter(value, _ptr);
        }
        void return_void() noexcept {}
        void unhandled_exception() { throw; }
        T &&result() noexcept {
            return std::forward<T>(*_ptr);
        }
        template<typename U> U &&await_transform(U &&value) = delete;
    };

    class Iterator {
    private:
        Handle _handle;
    public:
        Iterator() noexcept : _handle(nullptr) {}
        Iterator(Iterator &&rhs) noexcept = default;
        Iterator &operator=(Iterator &&rhs) noexcept = default;
        Iterator(const Iterator &rhs) = delete;
        Iterator &operator=(const Iterator &) = delete;
        explicit Iterator(Handle handle) : _handle(handle) {
            _handle.resume();
        }
        using iterator_concept = std::input_iterator_tag;
        using difference_type = std::ptrdiff_t;
        using value_type = std::remove_cvref_t<T>;
        bool operator==(std::default_sentinel_t) const {
            return _handle.done();
        }
        Iterator &operator++() {
            _handle.resume();
            return *this;
        }
        void operator++(int) {
            operator++();
        }
        decltype(auto) operator*() const {
            return std::forward<T>(_handle.promise().result());
        }
    };
    
private:
    Handle _handle;
    
public:
    Generator(const Generator &) = delete;
    Generator &operator=(const Generator &) = delete;
    explicit Generator(Handle handle_in) noexcept : _handle(handle_in) {}
    Generator(Generator &&rhs) noexcept : _handle(std::exchange(rhs._handle, nullptr)) {}
    ~Generator() {
        if (_handle) {
            _handle.destroy();
        }
    }
    auto begin() { return Iterator(_handle); }
    auto end() const noexcept { return std::default_sentinel_t(); }
};

}
