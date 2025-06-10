// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "wand_bench_setup.hpp"
#include <vespa/vespalib/gtest/gtest.h>

constexpr uint32_t docid_limit = 10000000;

TEST(WeakAndBenchTest, benchmark1) {
    VespaWandFactory f1(1000);
    WandSetup f2(f1, 10, docid_limit);
    f2.benchmark();
}
TEST(WeakAndBenchTest, benchmark2) {
    TermFrequencyRiseWandFactory f1(1000, docid_limit);
    WandSetup f2(f1, 10, docid_limit);
    f2.benchmark();
}
TEST(WeakAndBenchTest, benchmark3) {
    VespaWandFactory f1(1000);
    WandSetup f2(f1, 100, docid_limit);
    f2.benchmark();
}
TEST(WeakAndBenchTest, benchmark4) {
    TermFrequencyRiseWandFactory f1(1000, docid_limit);
    WandSetup f2(f1, 100, docid_limit);
    f2.benchmark();
}
TEST(WeakAndBenchTest, benchmark5) {
    VespaWandFactory f1(1000);
    WandSetup f2(f1, 1000, docid_limit);
    f2.benchmark();
}
TEST(WeakAndBenchTest, benchmark6) {
    TermFrequencyRiseWandFactory f1(1000, docid_limit);
    WandSetup f2(f1, 1000, docid_limit);
    f2.benchmark();
}

TEST(WeakAndBenchTest, benchmark7) {
    VespaWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 10, docid_limit);
    f3.benchmark();
}
TEST(WeakAndBenchTest, benchmark8) {
    TermFrequencyRiseWandFactory f1(1000, docid_limit);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 10, docid_limit);
    f3.benchmark();
}
TEST(WeakAndBenchTest, benchmark9) {
    VespaWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 100, docid_limit);
    f3.benchmark();
}
TEST(WeakAndBenchTest, benchmark10) {
    TermFrequencyRiseWandFactory f1(1000, docid_limit);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 100, docid_limit);
    f3.benchmark();
}
TEST(WeakAndBenchTest, benchmark11) {
    VespaWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 1000, docid_limit);
    f3.benchmark();
}
TEST(WeakAndBenchTest, benchmark12) {
    TermFrequencyRiseWandFactory f1(1000, docid_limit);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 1000, docid_limit);
    f3.benchmark();
}

GTEST_MAIN_RUN_ALL_TESTS()
