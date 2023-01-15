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
template <typename R, typename V = void>
class [[nodiscard]] Generator {
public:
    // these are from the std::generator proposal (P2502R2)
    using value_type = std::conditional_t<std::is_void_v<V>, std::remove_cvref_t<R>, V>;
    using ref_type   = std::conditional_t<std::is_void_v<V>, R &&, R>;
    using yield_type = std::conditional_t<std::is_reference_v<ref_type>, ref_type, const ref_type &>;
    using cref_yield = const std::remove_reference_t<yield_type> &;
    using ptr_type   = std::add_pointer_t<yield_type>;
    using cpy_type   = std::remove_cvref_t<yield_type>;
    static constexpr bool extra_yield = std::is_rvalue_reference_v<yield_type> && std::constructible_from<cpy_type, cref_yield>;

    class promise_type;
    using Handle = std::coroutine_handle<promise_type>;

    class promise_type {
    private:
        ptr_type _state;

        struct copy_awaiter : std::suspend_always {
            copy_awaiter(const cpy_type &value, ptr_type &ptr)
              : value_cpy(value)
            {
                ptr = std::addressof(value_cpy);
            }
            copy_awaiter(copy_awaiter&&) = delete;
            copy_awaiter(const copy_awaiter&) = delete;
            cpy_type value_cpy;
        };

    public:
        promise_type(promise_type &&) = delete;
        promise_type(const promise_type &) = delete;
        promise_type() noexcept : _state() {}
        Generator get_return_object() { return Generator(Handle::from_promise(*this)); }
        std::suspend_always initial_suspend() noexcept { return {}; }
        std::suspend_always final_suspend() noexcept { return {}; }
        std::suspend_always yield_value(yield_type value) noexcept {
            _state = std::addressof(value);
            return {};
        }
        auto yield_value(cref_yield value) requires extra_yield {
            return copy_awaiter(value, _state);
        }
        void return_void() noexcept {}
        void unhandled_exception() { throw; }
        ref_type result() noexcept {
            return static_cast<ref_type>(*_state);
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
        using value_type = Generator::value_type;
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
        ref_type operator*() const {
            return _handle.promise().result();
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
