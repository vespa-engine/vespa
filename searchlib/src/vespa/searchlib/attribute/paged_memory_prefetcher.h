// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/vespalib/util/will_need_advisor.h>

namespace search::attribute {

/*
 * Helper for implementing IAttributeVector::prefetch_docs() for attributes with paged (file backed) memory.
 *
 * Collects the memory holding the values of some documents, and hints the kernel that it will be read
 * soon. Attributes that need the values at one level to locate the memory at the next level (e.g. the
 * multi value mapping locating the values) call flush() between the levels: reading the first level is
 * then served by the reads already in flight, instead of taking one synchronous page fault at a time.
 */
class PagedMemoryPrefetcher {
    vespalib::alloc::WillNeedAdvisor _advisor;
    IAttributeVector::PrefetchResult _result;

public:
    explicit PagedMemoryPrefetcher(size_t num_docs);
    ~PagedMemoryPrefetcher();
    void add(const void* buf, size_t size) { _advisor.add(buf, size); }
    // Hint the kernel about the memory added so far.
    void flush();
    // Hint the kernel about the memory added so far, and return what was hinted in total.
    IAttributeVector::PrefetchResult result();
};

} // namespace search::attribute
