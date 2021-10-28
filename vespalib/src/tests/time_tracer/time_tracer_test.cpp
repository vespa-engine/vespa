// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/test/time_tracer.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using vespalib::test::TimeTracer;

TT_Tag tag0("tag0");
TT_Tag tag1("tag1");
TT_Tag tag2("tag2");

TT_Tag my_tag("my tag");

TEST("require that tag ids are equal if and only if tag names are equal") {
    TT_Tag tag1_too("tag1");
    EXPECT_NOT_EQUAL(tag0.id(), tag1.id());
    EXPECT_NOT_EQUAL(tag1.id(), tag2.id());
    EXPECT_NOT_EQUAL(tag2.id(), tag0.id());
    EXPECT_EQUAL(tag1_too.id(), tag1.id());
}

TEST_MT("require that threads are numbered by first sample", 3) {
    if (thread_id == 0) {
        { TT_Sample sample(tag0); }
        TEST_BARRIER(); // # 1
        TEST_BARRIER(); // # 2
    } else if (thread_id == 1) {
        TEST_BARRIER(); // # 1
        { TT_Sample sample(tag1); }
        TEST_BARRIER(); // # 2
    } else {
        TEST_BARRIER(); // # 1
        TEST_BARRIER(); // # 2
        { TT_Sample sample(tag2); }
    }
    TEST_BARRIER(); // # 3
    auto list = TimeTracer::extract().get();
    ASSERT_EQUAL(list.size(), 3u);
    EXPECT_EQUAL(list[0].thread_id, 0u);
    EXPECT_EQUAL(list[0].tag_id, tag0.id());
    EXPECT_EQUAL(list[1].thread_id, 1u);
    EXPECT_EQUAL(list[1].tag_id, tag1.id());
    EXPECT_EQUAL(list[2].thread_id, 2u);
    EXPECT_EQUAL(list[2].tag_id, tag2.id());
}

TEST("require that records are extracted inversely ordered by end time per thread") {
    { TT_Sample s(my_tag); }
    { TT_Sample s(my_tag); }
    { TT_Sample s(my_tag); }
    auto t = TimeTracer::now();
    auto list = TimeTracer::extract().get();
    EXPECT_EQUAL(list.size(), 6u);
    size_t cnt = 0;
    for (const auto &item: list) {
        if (item.tag_id == my_tag.id()) {
            ++cnt;
            EXPECT_TRUE(item.start <= item.stop);
            EXPECT_TRUE(item.stop <= t);
            t = item.stop;
        }
    }
    EXPECT_EQUAL(cnt, 3u);
}

TEST("benchmark time sampling") {
    double min_stamp_us = 1000000.0 * BenchmarkTimer::benchmark([]() noexcept { (void) TimeTracer::now(); }, 1.0);
    double min_sample_us = 1000000.0 * BenchmarkTimer::benchmark([]() noexcept { TT_Sample my_sample(my_tag); }, 1.0);
    fprintf(stderr, "min timestamp time: %g us\n", min_stamp_us);
    fprintf(stderr, "min sample time: %g us\n", min_sample_us);
    fprintf(stderr, "estimated non-clock overhead: %g us\n", (min_sample_us - (min_stamp_us * 2.0)));
    auto list = TimeTracer::extract().get();
    fprintf(stderr, "total samples after benchmarking: %zu\n", list.size());
    EXPECT_GREATER(list.size(), 6u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
