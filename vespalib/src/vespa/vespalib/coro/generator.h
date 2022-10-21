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
 * input_range. A generator is recursive (it may yield another
 * generator of the same type to include its values in the output).
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
        std::exception_ptr _exception;
        Handle *_itr_state;
        Handle _parent;

        template <bool check_exception>
        struct SwitchTo : std::suspend_always {
            Handle next;
            explicit SwitchTo(Handle next_in) : next(next_in) {}
            std::coroutine_handle<> await_suspend(Handle prev) const noexcept {
                if (next) {
                    Handle &itr_state = prev.promise().itr_state();
                    itr_state = next;
                    next.promise().itr_state(itr_state);
                    return next;
                } else {
                    return std::noop_coroutine();
                }
            }
            void await_resume() const noexcept(!check_exception) {
                if (check_exception && next.promise()._exception) {
                    std::rethrow_exception(next.promise()._exception);
                }
            }
        };

    public:
        promise_type(promise_type &&) = delete;
        promise_type(const promise_type &) = delete;
        promise_type() noexcept : _ptr(nullptr), _exception(), _itr_state(nullptr), _parent(nullptr) {}
        Generator<T> get_return_object() { return Generator(Handle::from_promise(*this)); }
        std::suspend_always initial_suspend() noexcept { return {}; }
        auto final_suspend() noexcept { return SwitchTo<false>(_parent); }
        std::suspend_always yield_value(T &&value) {
            _ptr = &value;
            return {};
        }
        auto yield_value(const T &value) requires(!std::is_reference_v<T> && std::copy_constructible<T>) {
            struct awaiter : std::suspend_always {
                awaiter(const T &value, Pointer &ptr) : value_cpy(value) {
                    ptr = std::addressof(value_cpy);
                }
                awaiter(awaiter&&) = delete;
                awaiter(const awaiter&) = delete;
                T value_cpy;
            };
            return awaiter(value, _ptr);
        }
        auto yield_value(Generator &&child) { return yield_value(child); }
        auto yield_value(Generator &child) {
            child._handle.promise()._parent = Handle::from_promise(*this);
            return SwitchTo<true>(child._handle);
        }
        void return_void() { _ptr = nullptr; }
        void unhandled_exception() {
            if (_parent) {
                _exception = std::current_exception();
            } else {
                throw;
            }
        }
        T &&result() {
            return std::forward<T>(*_ptr);
        }
        Pointer result_ptr() {
            return _ptr;
        }
        Handle &itr_state() const noexcept { return *_itr_state; }
        void itr_state(Handle &handle) noexcept { _itr_state = std::addressof(handle); }
        template<typename U> std::suspend_always await_transform(U &&value) = delete;
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
            _handle.promise().itr_state(_handle);
            _handle.resume();
        }
        using iterator_concept = std::input_iterator_tag;
        using difference_type = std::ptrdiff_t;
        using value_type = std::remove_cvref_t<T>;
        bool operator==(std::default_sentinel_t) const {
            return _handle.done();
        }
        Iterator &operator++() {
            _handle.promise().itr_state(_handle);
            _handle.resume();
            return *this;
        }
        void operator++(int) {
            operator++();
        }
        decltype(auto) operator*() const {
            return std::forward<T>(_handle.promise().result());
        }
        auto operator->() const {
            return _handle.promise().result_ptr();
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
