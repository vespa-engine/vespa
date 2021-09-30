// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <functional>

namespace vespalib {

// Convenience macro used to create a function that can be used as an
// init function when creating an executor to inject a frame with the
// given name into the stack of all worker threads.

#define VESPA_THREAD_STACK_TAG(name)         \
    int name(::vespalib::Runnable &worker) { \
        worker.run();                        \
        return 1;                            \
    }

/**
 * Interface implemented in order to be run by a Thread.
 **/
struct Runnable {
    using UP = std::unique_ptr<Runnable>;
    using init_fun_t = std::function<int(Runnable&)>;

    /**
     * Entry point called by the running thread
     **/
    virtual void run() = 0;

    /**
     * Empty virtual destructor to enable subclassing.
     **/
    virtual ~Runnable() {}
};

} // namespace vespalib

