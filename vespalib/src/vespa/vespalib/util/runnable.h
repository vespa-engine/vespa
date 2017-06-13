// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace vespalib {

/**
 * Interface implemented in order to be run by a Thread.
 **/
struct Runnable {
    typedef std::unique_ptr<Runnable> UP;

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

