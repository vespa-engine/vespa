// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "wand_bench_setup.hpp"
#include <vespa/vespalib/gtest/gtest.h>

TEST(ParallelWeakAndBenchTest, benchmark1) {
    VespaParallelWandFactory f1(1000);
    WandSetup f2(f1, 10, 10000000);
    f2.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark2) {
    DotProductRiseWandFactory f1(1000);
    WandSetup f2(f1, 10, 10000000);
    f2.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark3) {
    VespaParallelWandFactory f1(1000);
    WandSetup f2(f1, 100, 10000000);
    f2.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark4) {
    DotProductRiseWandFactory f1(1000);
    WandSetup f2(f1, 100, 10000000);
    f2.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark5) {
    VespaParallelWandFactory f1(1000);
    WandSetup f2(f1, 1000, 10000000);
    f2.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark6) {
    DotProductRiseWandFactory f1(1000);
    WandSetup f2(f1, 1000, 10000000);
    f2.benchmark();
}

TEST(ParallelWeakAndBenchTest, benchmark7) {
    VespaParallelWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 10, 10000000);
    f3.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark8) {
    DotProductRiseWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 10, 10000000);
    f3.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark9) {
    VespaParallelWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 100, 10000000);
    f3.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark10) {
    DotProductRiseWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 100, 10000000);
    f3.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark11) {
    VespaParallelWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 1000, 10000000);
    f3.benchmark();
}
TEST(ParallelWeakAndBenchTest, benchmark12) {
    DotProductRiseWandFactory f1(1000);
    FilterFactory f2(f1, 2);
    WandSetup f3(f2, 1000, 10000000);
    f3.benchmark();
}

GTEST_MAIN_RUN_ALL_TESTS()
