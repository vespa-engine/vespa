// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/**
 * Concurrent activity that we can wait for a conclusion of.
 **/
struct Joinable {
    /**
     * Wait for the conclusion of this concurrent activity
     **/
    virtual void join() = 0;

    /**
     * Empty virtual destructor to enable subclassing.
     **/
    virtual ~Joinable() {}
};

} // namespace vespalib

