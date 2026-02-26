// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "wand_bench_setup.hpp"
#include <vespa/vespalib/gtest/gtest.h>

using namespace rise;

namespace {

constexpr uint32_t NUM_CHILDREN=300;
constexpr uint32_t LIMIT = 5000000;

template <typename WeakAndType, typename RiseType>
void checkWandHits(WandFactory &vespa, WandFactory &rise, uint32_t step, uint32_t filter) {
    WandSetup vespaSetup(vespa, NUM_CHILDREN, LIMIT);
    WandSetup riseSetup(rise, NUM_CHILDREN, LIMIT);
    SearchIterator::UP s1 = vespaSetup.create();
    s1->initFullRange();
    SearchIterator::UP s2 = riseSetup.create();
    s2->initFullRange();
    ASSERT_TRUE(dynamic_cast<WeakAndType*>(s1.get()) != nullptr);
    ASSERT_TRUE(dynamic_cast<WeakAndType*>(s2.get()) == nullptr);
    ASSERT_TRUE(dynamic_cast<RiseType*>(s2.get()) != nullptr);
    ASSERT_TRUE(dynamic_cast<RiseType*>(s1.get()) == nullptr);
    s1->seek(1);
    s2->seek(1);
    while (!s1->isAtEnd() &&
           !s2->isAtEnd())
    {
        if (s1->getDocId() != s2->getDocId()) assert(true);
        ASSERT_EQ(s1->getDocId(), s2->getDocId());
        if ((filter == 0) || ((s1->getDocId() % filter) != 0)) {
            s1->unpack(s1->getDocId());
            s2->unpack(s2->getDocId());
        }
        s1->seek(s1->getDocId() + step);
        s2->seek(s2->getDocId() + step);
    }
    ASSERT_TRUE(s1->isAtEnd());
    ASSERT_TRUE(s2->isAtEnd());
}

} // namespace <unnamed>

TEST(WeakAndExpensiveTest, require_that_mod_search_works) {
    Stats stats;
    auto search = std::make_unique<ModSearch>(stats, 3, 8, 3, nullptr);
    SimpleResult hits;
    hits.search(*search, 100);
    EXPECT_EQ(SimpleResult().addHit(3).addHit(6), hits);
}

//---- WeakAndSearch ------------------------------------------------------------------------------

TEST(WeakAndExpensiveTest, require_that_array_WAND_and_RISE_WAND_gives_the_same_hits)
{
    VespaArrayWandFactory f1(NUM_CHILDREN, LIMIT);
    TermFrequencyRiseWandFactory f2(NUM_CHILDREN, LIMIT);
    checkWandHits<WeakAndSearch, TermFrequencyRiseWand>(f1, f2, 1, 0);
}

TEST(WeakAndExpensiveTest, require_that_heap_WAND_and_RISE_WAND_gives_the_same_hits)
{
    VespaHeapWandFactory f1(NUM_CHILDREN, LIMIT);
    TermFrequencyRiseWandFactory f2(NUM_CHILDREN, LIMIT);
    checkWandHits<WeakAndSearch, TermFrequencyRiseWand>(f1, f2, 1, 0);
}

TEST(WeakAndExpensiveTest, require_that_array_WAND_and_RISE_WAND_gives_the_same_hits_with_filtering_and_skipping)
{
    VespaArrayWandFactory f1(NUM_CHILDREN, LIMIT);
    TermFrequencyRiseWandFactory f2(NUM_CHILDREN, LIMIT);
    checkWandHits<WeakAndSearch, TermFrequencyRiseWand>(f1, f2, 123, 5);
}

TEST(WeakAndExpensiveTest, require_that_heap_WAND_and_RISE_WAND_gives_the_same_hits_with_filtering_and_skipping)
{
    VespaHeapWandFactory f1(NUM_CHILDREN, LIMIT);
    TermFrequencyRiseWandFactory f2(NUM_CHILDREN, LIMIT);
    checkWandHits<WeakAndSearch, TermFrequencyRiseWand>(f1, f2, 123, 5);
}


//---- ParallelWeakAndSearch ----------------------------------------------------------------------

TEST(WeakAndExpensiveTest, require_that_array_PWAND_and_RISE_WAND_gives_the_same_hits)
{
    VespaParallelArrayWandFactory f1(NUM_CHILDREN);
    DotProductRiseWandFactory f2(NUM_CHILDREN);
    checkWandHits<ParallelWeakAndSearch, DotProductRiseWand>(f1, f2, 1, 0);
}

TEST(WeakAndExpensiveTest, require_that_heap_PWAND_and_RISE_WAND_gives_the_same_hits)
{
    VespaParallelHeapWandFactory f1(NUM_CHILDREN);
    DotProductRiseWandFactory f2(NUM_CHILDREN);
    checkWandHits<ParallelWeakAndSearch, DotProductRiseWand>(f1, f2, 1, 0);
}

TEST(WeakAndExpensiveTest, require_that_array_PWAND_and_RISE_WAND_gives_the_same_hits_with_filtering_and_skipping)
{
    VespaParallelArrayWandFactory f1(NUM_CHILDREN);
    DotProductRiseWandFactory f2(NUM_CHILDREN);
    checkWandHits<ParallelWeakAndSearch, DotProductRiseWand>(f1, f2, 123, 5);
}

TEST(WeakAndExpensiveTest, require_that_heap_PWAND_and_RISE_WAND_gives_the_same_hits_with_filtering_and_skipping)
{
    VespaParallelHeapWandFactory f1(NUM_CHILDREN);
    DotProductRiseWandFactory f2(NUM_CHILDREN);
    checkWandHits<ParallelWeakAndSearch, DotProductRiseWand>(f1, f2, 123, 5);
}

GTEST_MAIN_RUN_ALL_TESTS()
