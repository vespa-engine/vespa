// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <variant>
#include <exception>
#include <stdexcept>

namespace vespalib::coro {

struct UnavailableResultException : std::runtime_error {
    using std::runtime_error::runtime_error;
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
public:
    Received() : _value() {}
    void set_value(T value) { _value.template emplace<1>(std::move(value)); }
    void set_error(std::exception_ptr exception) { _value.template emplace<0>(exception); }
    void set_done() { _value.template emplace<0>(nullptr); }
    bool has_value() const { return (_value.index() == 1); }
    bool has_error() const { return (_value.index() == 0) && bool(std::get<0>(_value)); }
    bool was_canceled() const { return !has_value() && !has_error(); }
    std::exception_ptr get_error() const { return has_error() ? std::get<0>(_value) : std::exception_ptr(); }
    T get_value() {
        if (_value.index() == 1) {
            return std::move(std::get<1>(_value));
        } else {
            if (auto ex = std::get<0>(_value)) {
                std::rethrow_exception(ex);
            } else {
                throw UnavailableResultException("tried to access the result of a canceled operation");
            }
        }
    }
};

}
