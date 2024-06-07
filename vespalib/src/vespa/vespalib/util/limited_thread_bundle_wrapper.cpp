// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "limited_thread_bundle_wrapper.h"
#include "exceptions.h"

namespace vespalib {

LimitedThreadBundleWrapper::LimitedThreadBundleWrapper(ThreadBundle& thread_bundle, uint32_t max_threads)
    : _thread_bundle(thread_bundle),
      _max_threads(std::min(max_threads, static_cast<uint32_t>(thread_bundle.size())))
{
}

LimitedThreadBundleWrapper::~LimitedThreadBundleWrapper() = default;

size_t
LimitedThreadBundleWrapper::size() const
{
    return _max_threads;
}

void
LimitedThreadBundleWrapper::run(Runnable* const* targets, size_t cnt)
{
    if (cnt > size()) {
        throw IllegalArgumentException("too many targets");
    }
    _thread_bundle.run(targets, cnt);
}

}
