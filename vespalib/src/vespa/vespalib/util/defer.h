// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <concepts>
#include <utility>

namespace vespalib {

// Do something at scope exit, similar to defer from go
template <std::invocable F>
class defer {
private:
    F _deferred;
public:
    explicit defer(F &&f) noexcept : _deferred(std::move(f)) {}
    ~defer() noexcept { _deferred(); }
    defer(defer &&) = delete;
    defer(const defer &) = delete;
    defer &operator=(defer &&) = delete;
    defer &operator=(const defer &) = delete;
};

template <typename F>
defer(F) -> defer<std::decay_t<F>>;

}
