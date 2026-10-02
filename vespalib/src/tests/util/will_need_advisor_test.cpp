// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/mmap_file_allocator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/will_need_advisor.h>

#include <unistd.h>

#include <cstring>
#include <filesystem>

using vespalib::alloc::MmapFileAllocator;
using vespalib::alloc::WillNeedAdvisor;
using Range = WillNeedAdvisor::Range;
using Ranges = std::vector<Range>;

namespace {

constexpr size_t ps = 4096;

Ranges merged(Ranges ranges) {
    WillNeedAdvisor::merge_page_ranges(ranges, ps);
    return ranges;
}

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

TEST(WillNeedAdvisorTest, empty_ranges_are_ignored) {
    WillNeedAdvisor advisor;
    char            buf[16];
    advisor.add(nullptr, 100);
    advisor.add(buf, 0);
    EXPECT_TRUE(advisor.empty());
    auto result = advisor.advise();
    EXPECT_EQ(0u, result.ranges);
    EXPECT_EQ(0u, result.bytes);
}

TEST(WillNeedAdvisorTest, file_backed_memory_can_be_advised) {
    std::string basedir("will-need-advisor-dir");
    {
        MmapFileAllocator allocator(basedir);
        auto              buf = allocator.alloc(1_Mi);
        ASSERT_NE(nullptr, buf.get());
        memset(buf.get(), 1, buf.size());
        auto            page_size = static_cast<size_t>(getpagesize());
        auto*           data = static_cast<char*>(buf.get());
        WillNeedAdvisor advisor;
        advisor.add(data + 10 * page_size + 3, 100);
        advisor.add(data + 11 * page_size, 1);
        advisor.add(data + 100 * page_size, 2 * page_size);
        auto result = advisor.advise();
        EXPECT_EQ(2u, result.ranges);
        EXPECT_EQ(4 * page_size, result.bytes);
        EXPECT_TRUE(advisor.empty());
        // advising again is a no-op
        result = advisor.advise();
        EXPECT_EQ(0u, result.ranges);
        allocator.free(buf);
    }
    std::filesystem::remove_all(std::filesystem::path(basedir));
}
