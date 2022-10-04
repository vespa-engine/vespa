// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "detached.h"
#include "lazy.h"
#include <coroutine>
#include <vespa/vespalib/util/gate.h>

namespace vespalib::coro {

template <typename T, typename S>
Detached signal_when_done(Lazy<T> &value, S &sink) {
    sink(co_await value);
}

template <typename T>
T &sync_wait(Lazy<T> &value) {
    struct MySink {
        Gate gate;
        T *result;
        void operator()(T &result_in) {
            result = &result_in;
            gate.countDown();
        }
        MySink() : gate(), result(nullptr) {}
    };
    MySink sink;
    signal_when_done(value, sink);
    sink.gate.await();
    return *sink.result;
}

}
