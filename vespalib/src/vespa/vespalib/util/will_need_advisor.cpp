// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "will_need_advisor.h"

#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>

namespace vespalib::alloc {

namespace {

const size_t page_size = getpagesize();

}

WillNeedAdvisor::WillNeedAdvisor() : _ranges() {
}

WillNeedAdvisor::~WillNeedAdvisor() = default;

void WillNeedAdvisor::add(const void* buf, size_t size) {
    if (buf == nullptr || size == 0) {
        return;
    }
    auto start = reinterpret_cast<uintptr_t>(buf);
    _ranges.push_back(Range{start, start + size});
}

WillNeedAdvisor::Result WillNeedAdvisor::advise() {
    Result result;
    merge_page_ranges(_ranges, page_size);
    for (const auto& range : _ranges) {
        size_t size = range.end - range.start;
        // Best effort, e.g. ENOMEM for a range that is no longer mapped is harmless.
        (void)madvise(reinterpret_cast<void*>(range.start), size, MADV_WILLNEED);
        ++result.ranges;
        result.bytes += size;
    }
    _ranges.clear();
    return result;
}

void WillNeedAdvisor::merge_page_ranges(std::vector<Range>& ranges, size_t page_size_in) {
    const uintptr_t page_mask = ~static_cast<uintptr_t>(page_size_in - 1);
    for (auto& range : ranges) {
        range.start &= page_mask;
        range.end = (range.end + (page_size_in - 1)) & page_mask;
    }
    std::sort(ranges.begin(), ranges.end(), [](const Range& a, const Range& b) { return a.start < b.start; });
    auto out = ranges.begin();
    for (auto it = ranges.begin(); it != ranges.end(); ++it) {
        if (out != ranges.begin() && it->start <= (out - 1)->end) {
            (out - 1)->end = std::max((out - 1)->end, it->end);
        } else {
            *out++ = *it;
        }
    }
    ranges.erase(out, ranges.end());
}

} // namespace vespalib::alloc
