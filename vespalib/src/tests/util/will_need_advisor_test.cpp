// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/mmap_file_allocator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/will_need_advisor.h>

#include <sys/mman.h>
#include <unistd.h>

#include <cstring>

using vespalib::alloc::MmapFileAllocator;
using vespalib::alloc::PtrAndSize;
using vespalib::alloc::WillNeedAdvisor;
using Method = WillNeedAdvisor::Method;
using Range = WillNeedAdvisor::Range;
using Ranges = std::vector<Range>;

namespace {

constexpr size_t ps = 4096;

Ranges merged(Ranges ranges, size_t max_range_size = WillNeedAdvisor::default_max_range_size) {
    WillNeedAdvisor::merge_page_ranges(ranges, ps, max_range_size);
    return ranges;
}

const size_t page_size = getpagesize();

// File backed memory, as used by paged attributes. The allocator removes its directory when destroyed.
struct FileBackedMemory {
    MmapFileAllocator allocator;
    PtrAndSize        buf;
    explicit FileBackedMemory(size_t size) : allocator("will-need-advisor-dir"), buf(allocator.alloc(size)) {
        memset(buf.get(), 1, buf.size());
    }
    ~FileBackedMemory() { allocator.free(buf); }
    char* page(size_t idx) const { return static_cast<char*>(buf.get()) + idx * page_size; }
};

} // namespace

TEST(WillNeedAdvisorTest, empty_input_gives_no_ranges) {
    EXPECT_EQ(Ranges(), merged({}));
}

TEST(WillNeedAdvisorTest, ranges_are_widened_to_page_boundaries) {
    EXPECT_EQ(Ranges({{ps, 2 * ps}}), merged({{ps + 10, ps + 20}}));
    EXPECT_EQ(Ranges({{ps, 3 * ps}}), merged({{2 * ps - 1, 2 * ps + 1}}));
    EXPECT_EQ(Ranges({{ps, 2 * ps}}), merged({{ps, 2 * ps}}));
}

TEST(WillNeedAdvisorTest, ranges_are_sorted_and_overlapping_or_adjacent_ranges_are_merged) {
    EXPECT_EQ(Ranges({{0, ps}, {3 * ps, 5 * ps}, {10 * ps, 11 * ps}}),
              merged({{10 * ps + 5, 10 * ps + 6}, {4 * ps + 1, 4 * ps + 2}, {3 * ps + 100, 3 * ps + 200}, {7, 9}}));
    // adjacent pages are merged
    EXPECT_EQ(Ranges({{0, 3 * ps}}), merged({{2 * ps, 2 * ps + 1}, {0, 1}, {ps, ps + 1}}));
    // a range contained in another one
    EXPECT_EQ(Ranges({{0, 8 * ps}}), merged({{0, 8 * ps}, {2 * ps, 3 * ps}}));
}

TEST(WillNeedAdvisorTest, long_ranges_are_split_into_chunks) {
    EXPECT_EQ(Ranges({{0, 32 * ps}, {32 * ps, 64 * ps}, {64 * ps, 75 * ps}, {100 * ps, 101 * ps}}),
              merged({{0, 75 * ps}, {100 * ps, 100 * ps + 1}}));
    EXPECT_EQ(Ranges({{0, 2 * ps}, {2 * ps, 3 * ps}}), merged({{0, 3 * ps}}, 2 * ps + 100));
    // chunks are at least one page
    EXPECT_EQ(Ranges({{0, ps}, {ps, 2 * ps}}), merged({{0, 2 * ps}}, 10));
}

TEST(WillNeedAdvisorTest, empty_ranges_are_ignored) {
    WillNeedAdvisor advisor;
    char            buf[16];
    advisor.add(nullptr, 100);
    advisor.add(buf, 0);
    EXPECT_TRUE(advisor.empty());
    auto result = advisor.advise();
    EXPECT_EQ(0u, result.ranges);
    EXPECT_EQ(0u, result.bytes);
    EXPECT_EQ(0u, result.syscalls);
}

TEST(WillNeedAdvisorTest, file_backed_memory_can_be_advised_per_range) {
    FileBackedMemory mem(1_Mi);
    WillNeedAdvisor  advisor;
    advisor.add(mem.page(10) + 3, 100);
    advisor.add(mem.page(11), 1);
    advisor.add(mem.page(100), 2 * page_size);
    auto result = advisor.advise(Method::PER_RANGE);
    EXPECT_EQ(2u, result.ranges);
    EXPECT_EQ(4 * page_size, result.bytes);
    EXPECT_EQ(2u, result.syscalls);
    EXPECT_TRUE(advisor.empty());
    // advising again is a no-op
    result = advisor.advise();
    EXPECT_EQ(0u, result.ranges);
}

TEST(WillNeedAdvisorTest, file_backed_memory_can_be_advised_batched) {
    FileBackedMemory mem(1_Mi);
    WillNeedAdvisor  advisor;
    for (size_t i = 0; i < 10; ++i) {
        advisor.add(mem.page(10 * i + 5), 10);
    }
    auto result = advisor.advise(Method::BATCHED);
    EXPECT_EQ(10u, result.ranges);
    EXPECT_EQ(10 * page_size, result.bytes);
    if (WillNeedAdvisor::batching_disabled()) {
        fprintf(stderr, "process_madvise is not usable here, fell back to madvise per range\n");
        EXPECT_LE(10u, result.syscalls);
    } else {
        EXPECT_EQ(1u, result.syscalls);
    }
}

TEST(WillNeedAdvisorTest, failing_ranges_are_not_counted) {
    FileBackedMemory mem(1_Mi);
    // reserve and release an address range, so it is (very likely) unmapped
    void* hole = mmap(nullptr, 4 * page_size, PROT_READ, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    ASSERT_NE(MAP_FAILED, hole);
    ASSERT_EQ(0, munmap(hole, 4 * page_size));
    for (auto method : {Method::PER_RANGE, Method::BATCHED}) {
        bool            batching_was_disabled = WillNeedAdvisor::batching_disabled();
        WillNeedAdvisor advisor;
        advisor.add(mem.page(1), 1);
        advisor.add(hole, 1);
        advisor.add(mem.page(3), 1);
        auto result = advisor.advise(method);
        EXPECT_EQ(2u, result.ranges);
        EXPECT_EQ(2 * page_size, result.bytes);
        // a failing range must not disable batching for the process
        EXPECT_EQ(batching_was_disabled, WillNeedAdvisor::batching_disabled());
    }
}
