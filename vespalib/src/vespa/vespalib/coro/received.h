// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <concepts>
#include <variant>
#include <exception>
#include <stdexcept>
#include <future>

namespace vespalib::coro {

struct UnavailableResultException : std::runtime_error {
    using std::runtime_error::runtime_error;
};

// concept indicating that R may be used to receive T
template <typename R, typename T>
concept receiver_of = requires(R r, T t, std::exception_ptr e) {
    r.set_value(std::move(t));
    r.set_error(e);
    r.set_done();
};

// concept indicating that R is a completion callback accepting T
template <typename R, typename T>
concept completion_callback_for = requires(R r, T t) {
    r(std::move(t));
};

/**
 * Simple value wrapper that stores the result observed by a receiver
 * (value/error/done). A receiver is the continuation of an
 * asynchronous operation in the world of executors.
 **/
template <std::movable T>
class Received {
private:
    std::variant<std::exception_ptr,T> _value;
    std::exception_ptr normalize_error() const {
        if (auto ex = std::get<0>(_value)) {
            return ex;
        } else {
            return std::make_exception_ptr(UnavailableResultException("tried to access the result of a canceled operation"));
        }
    }
public:
    Received() : _value() {}
    template <typename RET>
    void set_value(RET &&value) { _value.template emplace<1>(std::forward<RET>(value)); }
    void set_error(std::exception_ptr exception) { _value.template emplace<0>(exception); }
    void set_done() { _value.template emplace<0>(nullptr); }
    bool has_value() const { return (_value.index() == 1); }
    bool has_error() const { return (_value.index() == 0) && bool(std::get<0>(_value)); }
    bool was_canceled() const { return !has_value() && !has_error(); }
    std::exception_ptr get_error() const {
        return has_value() ? std::exception_ptr() : std::get<0>(_value);
    }
    T &get_value() & {
        if (_value.index() == 1) {
            return std::get<1>(_value);
        } else {
            std::rethrow_exception(normalize_error());
        }
    }
    T &&get_value() && { return std::move(get_value()); }
    template <typename R>
    requires completion_callback_for<R,Received>
    void forward(R &&r) {
        r(std::move(*this));
    }
    template <typename R>
    requires receiver_of<R,T>
    void forward(R &r) {
        if (_value.index() == 1) {
            r.set_value(std::get<1>(std::move(_value)));
        } else {
            if (auto ex = std::get<0>(_value)) {
                r.set_error(ex);
            } else {
                r.set_done();
            }
        }
    }
    void forward(std::promise<T> &r) {
        if (_value.index() == 1) {
            r.set_value(std::get<1>(std::move(_value)));
        } else {
            r.set_exception(normalize_error());
        }
    }
};

static_assert(receiver_of<Received<int>, int>);
static_assert(receiver_of<Received<std::unique_ptr<int>>, std::unique_ptr<int>>);

}
