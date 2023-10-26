// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/queryeval/wand/weak_and_heap.h>

using namespace search::queryeval;
using score_t = wand::score_t;

struct Scores : public std::vector<score_t> {
    Scores &s(score_t val) {
        push_back(val);
        return *this;
    }
};

void
adjust(WeakAndHeap &heap, const Scores &scores) 
{
    Scores tmp = scores;
    heap.adjust(&tmp[0], &tmp[0] + tmp.size());
}

void
assertScores(const Scores &exp, SharedWeakAndPriorityQueue &heap)
{
    ASSERT_EQUAL(exp.size(), heap.getScores().size());
    for (size_t i = 0; i < exp.size(); ++i) {
        score_t front = heap.getScores().front();
        EXPECT_EQUAL(exp[i], front);
        heap.getScores().pop_front();
    }
}

struct NullFixture {
    SharedWeakAndPriorityQueue h;
    NullFixture() : h(0) {}
};

struct EmptyFixture {
    SharedWeakAndPriorityQueue h;
    EmptyFixture() : h(4) {}
};

struct FilledFixture {
    SharedWeakAndPriorityQueue h;
    FilledFixture() : h(4) {
        adjust(h, Scores().s(3).s(5).s(7).s(9));
        EXPECT_EQUAL(3, h.getMinScore());
    }
};

TEST_F("require that SharedWeakAndPriorityQueue with 0 size gives max threshold", NullFixture)
{
    EXPECT_EQUAL(std::numeric_limits<score_t>::max(), f.h.getMinScore());
    adjust(f.h, Scores().s(100));
    EXPECT_EQUAL(std::numeric_limits<score_t>::max(), f.h.getMinScore());
}

TEST_F("require that SharedWeakAndPriorityQueue can be filled one-by-one", EmptyFixture)
{
    adjust(f.h, Scores().s(4));
    EXPECT_EQUAL(0, f.h.getMinScore());
    adjust(f.h, Scores().s(3));
    EXPECT_EQUAL(0, f.h.getMinScore());
    adjust(f.h, Scores().s(2));
    EXPECT_EQUAL(0, f.h.getMinScore());
    adjust(f.h, Scores().s(1));
    EXPECT_EQUAL(1, f.h.getMinScore());
    assertScores(Scores().s(1).s(2).s(3).s(4), f.h);
}

TEST_F("require that SharedWeakAndPriorityQueue can be filled all-at-once", EmptyFixture)
{
    adjust(f.h, Scores().s(4).s(3).s(2).s(1));
    EXPECT_EQUAL(1, f.h.getMinScore());
    assertScores(Scores().s(1).s(2).s(3).s(4), f.h);
}

TEST_F("require that SharedWeakAndPriorityQueue can be adjusted one-by-one", FilledFixture)
{
    adjust(f.h, Scores().s(2));
    EXPECT_EQUAL(3, f.h.getMinScore());
    adjust(f.h, Scores().s(3));
    EXPECT_EQUAL(3, f.h.getMinScore());
    adjust(f.h, Scores().s(6));
    EXPECT_EQUAL(5, f.h.getMinScore());
    adjust(f.h, Scores().s(8));
    EXPECT_EQUAL(6, f.h.getMinScore());
    adjust(f.h, Scores().s(4));
    EXPECT_EQUAL(6, f.h.getMinScore());
    assertScores(Scores().s(6).s(7).s(8).s(9), f.h);
}

TEST_F("require that SharedWeakAndPriorityQueue can be adjusted all-at-once", FilledFixture)
{
    adjust(f.h, Scores().s(2).s(3).s(6).s(8).s(4));
    EXPECT_EQUAL(6, f.h.getMinScore());
    assertScores(Scores().s(6).s(7).s(8).s(9), f.h);
}

TEST_MAIN() { TEST_RUN_ALL(); }
