// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "detached.h"
#include "lazy.h"
#include <vespa/vespalib/util/gate.h>

#include <coroutine>
#include <exception>

namespace vespalib::coro {

template <typename T, typename S>
Detached signal_when_done(Lazy<T> &value, S &sink) {
    try {
        sink(co_await value);
    } catch (...) {
        sink(std::current_exception());
    }
}

/**
 * Wait for a lazy value to be calculated (note that waiting for a
 * value will also start calculating it). Make sure the thread waiting
 * is not needed in the calculation of the value, or you will end up
 * with a deadlock.
 **/
template <typename T>
T &sync_wait(Lazy<T> &value) {
    struct MySink {
        Gate gate;
        T *result;
        std::exception_ptr exception;
        void operator()(T &result_in) {
            result = &result_in;
            gate.countDown();
        }
        void operator()(std::exception_ptr exception_in) {
            exception = exception_in;
            gate.countDown();
        }
        MySink() : gate(), result(nullptr), exception() {}
    };
    MySink sink;
    signal_when_done(value, sink);
    sink.gate.await();
    if (sink.exception) {
        std::rethrow_exception(sink.exception);
    }
    return *sink.result;
}

template <typename T>
T &&sync_wait(Lazy<T> &&value) {
    return std::move(sync_wait(value));
}

}
