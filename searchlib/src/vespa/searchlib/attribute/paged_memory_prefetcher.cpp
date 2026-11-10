// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "paged_memory_prefetcher.h"

#include <vespa/vespalib/util/mmap_file_allocator_factory.h>

using vespalib::alloc::MmapFileAllocatorFactory;

namespace search::attribute {

PagedMemoryPrefetcher::PagedMemoryPrefetcher(size_t num_docs)
    : _advisor(MmapFileAllocatorFactory::instance().max_readahead_size()), _result() {
    _advisor.reserve(num_docs);
}

PagedMemoryPrefetcher::~PagedMemoryPrefetcher() = default;

void PagedMemoryPrefetcher::flush() {
    if (!_advisor.empty()) {
        auto advised = _advisor.advise();
        _result.ranges += advised.ranges;
        _result.bytes += advised.bytes;
    }
}

IAttributeVector::PrefetchResult PagedMemoryPrefetcher::result() {
    flush();
    return _result;
}

} // namespace search::attribute
