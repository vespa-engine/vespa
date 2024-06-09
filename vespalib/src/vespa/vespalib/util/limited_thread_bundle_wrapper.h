// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "thread_bundle.h"

namespace vespalib {

/*
 * A ThreadBundle implementation that limits the number of available threads
 * from the backing thread bundle.
 */
class LimitedThreadBundleWrapper : public ThreadBundle
{
    ThreadBundle&  _thread_bundle;
    const uint32_t _max_threads;
public:
    LimitedThreadBundleWrapper(ThreadBundle& thread_bundle, uint32_t max_threadss);
    ~LimitedThreadBundleWrapper() override;
    size_t size() const override;
    void run(Runnable* const* targets, size_t cnt) override;
};

}
