// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/test/time_tracer.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using vespalib::test::TimeTracer;
using vespalib::test::Nexus;

TT_Tag tag0("tag0");
TT_Tag tag1("tag1");
TT_Tag tag2("tag2");

TT_Tag my_tag("my tag");

TEST(TimeTracerTest, require_that_tag_ids_are_equal_if_and_only_if_tag_names_are_equal) {
    TT_Tag tag1_too("tag1");
    EXPECT_NE(tag0.id(), tag1.id());
    EXPECT_NE(tag1.id(), tag2.id());
    EXPECT_NE(tag2.id(), tag0.id());
    EXPECT_EQ(tag1_too.id(), tag1.id());
}

TEST(TimeTracerTest, require_that_threads_are_numbered_by_first_sample) {
    size_t num_threads = 3;
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    if (thread_id == 0) {
                        { TT_Sample sample(tag0); }
                        ctx.barrier(); // # 1
                        ctx.barrier(); // # 2
                    } else if (thread_id == 1) {
                        ctx.barrier(); // # 1
                        { TT_Sample sample(tag1); }
                        ctx.barrier(); // # 2
                    } else {
                        ctx.barrier(); // # 1
                        ctx.barrier(); // # 2
                        { TT_Sample sample(tag2); }
                    }
                    ctx.barrier(); // # 3
                    auto list = TimeTracer::extract().get();
                    if (list.size() == 3u) {
                        EXPECT_EQ(list[0].thread_id, 0u);
                        EXPECT_EQ(list[0].tag_id, tag0.id());
                        EXPECT_EQ(list[1].thread_id, 1u);
                        EXPECT_EQ(list[1].tag_id, tag1.id());
                        EXPECT_EQ(list[2].thread_id, 2u);
                        EXPECT_EQ(list[2].tag_id, tag2.id());
                    } else {
                        EXPECT_EQ(list.size(), 3u);
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(TimeTracerTest, require_that_records_are_extracted_inversely_ordered_by_end_time_per_thread) {
    { TT_Sample s(my_tag); }
    { TT_Sample s(my_tag); }
    { TT_Sample s(my_tag); }
    auto t = TimeTracer::now();
    auto list = TimeTracer::extract().get();
    EXPECT_EQ(list.size(), 6u);
    size_t cnt = 0;
    for (const auto &item: list) {
        if (item.tag_id == my_tag.id()) {
            ++cnt;
            EXPECT_TRUE(item.start <= item.stop);
            EXPECT_TRUE(item.stop <= t);
            t = item.stop;
        }
    }
    EXPECT_EQ(cnt, 3u);
}

TEST(TimeTracerTest, benchmark_time_sampling) {
    double min_stamp_us = 1000000.0 * BenchmarkTimer::benchmark([]() noexcept { (void) TimeTracer::now(); }, 1.0);
    double min_sample_us = 1000000.0 * BenchmarkTimer::benchmark([]() noexcept { TT_Sample my_sample(my_tag); }, 1.0);
    fprintf(stderr, "min timestamp time: %g us\n", min_stamp_us);
    fprintf(stderr, "min sample time: %g us\n", min_sample_us);
    fprintf(stderr, "estimated non-clock overhead: %g us\n", (min_sample_us - (min_stamp_us * 2.0)));
    auto list = TimeTracer::extract().get();
    fprintf(stderr, "total samples after benchmarking: %zu\n", list.size());
    EXPECT_GT(list.size(), 6u);
}

GTEST_MAIN_RUN_ALL_TESTS()
