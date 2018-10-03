// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchcore/proton/matching/match_loop_communicator.h>
#include <vespa/vespalib/util/box.h>

using namespace proton::matching;

using vespalib::Box;
using vespalib::make_box;

using Range = MatchLoopCommunicator::Range;
using RangePair = MatchLoopCommunicator::RangePair;
using Matches = MatchLoopCommunicator::Matches;
using Hit = MatchLoopCommunicator::Hit;
using Hits = MatchLoopCommunicator::Hits;
using search::queryeval::SortedHitSequence;

Hits makeScores(size_t id) {
    switch (id) {
    case 0: return make_box<Hit>({1, 5.4}, {2, 4.4}, {3, 3.4}, {4, 2.4}, {5, 1.4});
    case 1: return make_box<Hit>({11, 5.3}, {12, 4.3}, {13, 3.3}, {14, 2.3}, {15, 1.3});
    case 2: return make_box<Hit>({21, 5.2}, {22, 4.2}, {23, 3.2}, {24, 2.2}, {25, 1.2});
    case 3: return make_box<Hit>({31, 5.1}, {32, 4.1}, {33, 3.1}, {34, 2.1}, {35, 1.1});
    case 4: return make_box<Hit>({41, 5.0}, {42, 4.0}, {43, 3.0}, {44, 2.0}, {45, 1.0});
    }
    return Box<Hit>();
}

Hits selectBest(MatchLoopCommunicator &com, const Hits &hits) {
    std::vector<uint32_t> refs;
    for (size_t i = 0; i < hits.size(); ++i) {
        refs.push_back(i);
    }
    return com.selectBest(SortedHitSequence(&hits[0], &refs[0], refs.size()));
}

RangePair makeRanges(size_t id) {
    switch (id) {
    case 0: return std::make_pair(Range(5, 5), Range(7, 7));
    case 1: return std::make_pair(Range(2, 2), Range(8, 8));
    case 2: return std::make_pair(Range(3, 3), Range(6, 6));
    case 3: return std::make_pair(Range(1, 1), Range(5, 5));
    case 4: return std::make_pair(Range(4, 4), Range(9, 9));
    }
    return std::make_pair(Range(-50, -60), Range(60, 50));
}

void equal(size_t count, const Hits & a, const Hits & b) {
    EXPECT_EQUAL(count, b.size());
    for (size_t i(0); i < count; i++) {
        EXPECT_EQUAL(a[i].first, b[i].first);
        EXPECT_EQUAL(a[i].second , b[i].second);
    }
}

void equal_range(const Range &a, const Range &b) {
    EXPECT_EQUAL(a.isValid(), b.isValid());
    EXPECT_EQUAL(a.low, b.low);
    EXPECT_EQUAL(a.high, b.high);
}

struct EveryOdd : public search::queryeval::IDiversifier {
    bool accepted(uint32_t docId) override {
        return docId & 0x01;
    }
};

TEST_F("require that selectBest gives appropriate results for single thread", MatchLoopCommunicator(num_threads, 3)) {
    TEST_DO(equal(2u, make_box<Hit>({1, 5}, {2, 4}), selectBest(f1, make_box<Hit>({1, 5}, {2, 4}))));
    TEST_DO(equal(3u, make_box<Hit>({1, 5}, {2, 4}, {3, 3}), selectBest(f1, make_box<Hit>({1, 5}, {2, 4}, {3, 3}))));
    TEST_DO(equal(3u, make_box<Hit>({1, 5}, {2, 4}, {3, 3}), selectBest(f1, make_box<Hit>({1, 5}, {2, 4}, {3, 3}, {4, 2}))));
}

TEST_F("require that selectBest gives appropriate results for single thread with filter",
       MatchLoopCommunicator(num_threads, 3, std::make_unique<EveryOdd>()))
{
    TEST_DO(equal(1u, make_box<Hit>({1, 5}), selectBest(f1, make_box<Hit>({1, 5}, {2, 4}))));
    TEST_DO(equal(2u, make_box<Hit>({1, 5}, {3, 3}), selectBest(f1, make_box<Hit>({1, 5}, {2, 4}, {3, 3}))));
    TEST_DO(equal(3u, make_box<Hit>({1, 5}, {3, 3}, {5, 1}), selectBest(f1, make_box<Hit>({1, 5}, {2, 4}, {3, 3}, {4, 2}, {5, 1}, {6, 0}))));
}

TEST_MT_F("require that selectBest works with no hits", 10, MatchLoopCommunicator(num_threads, 10)) {
    EXPECT_TRUE(selectBest(f1, Box<Hit>()).empty());
}

TEST_MT_F("require that selectBest works with too many hits from all threads", 5, MatchLoopCommunicator(num_threads, 13)) {
    if (thread_id < 3) {
        TEST_DO(equal(3u, makeScores(thread_id), selectBest(f1, makeScores(thread_id))));
    } else {
        TEST_DO(equal(2u, makeScores(thread_id), selectBest(f1, makeScores(thread_id))));
    }
}

TEST_MT_F("require that selectBest works with some exhausted threads", 5, MatchLoopCommunicator(num_threads, 22)) {
    if (thread_id < 2) {
        TEST_DO(equal(5u, makeScores(thread_id), selectBest(f1, makeScores(thread_id))));
    } else {
        TEST_DO(equal(4u, makeScores(thread_id), selectBest(f1, makeScores(thread_id))));
    }
}

TEST_MT_F("require that selectBest can select all hits from all threads", 5, MatchLoopCommunicator(num_threads, 100)) {
    EXPECT_EQUAL(5u, selectBest(f1, makeScores(thread_id)).size());
}

TEST_MT_F("require that selectBest works with some empty threads", 10, MatchLoopCommunicator(num_threads, 7)) {
    if (thread_id < 2) {
        TEST_DO(equal(2u, makeScores(thread_id), selectBest(f1, makeScores(thread_id))));
    } else if (thread_id < 5) {
        TEST_DO(equal(1u, makeScores(thread_id), selectBest(f1, makeScores(thread_id))));
    } else {
        EXPECT_TRUE(selectBest(f1, makeScores(thread_id)).empty());
    }
}

TEST_F("require that rangeCover is identity function for single thread", MatchLoopCommunicator(num_threads, 5)) {
    RangePair res = f1.rangeCover(std::make_pair(Range(2, 4), Range(3, 5)));
    TEST_DO(equal_range(Range(2, 4), res.first));
    TEST_DO(equal_range(Range(3, 5), res.second));
}

TEST_MT_F("require that rangeCover can mix ranges from multiple threads", 5, MatchLoopCommunicator(num_threads, 5)) {
    RangePair res = f1.rangeCover(makeRanges(thread_id));
    TEST_DO(equal_range(Range(1, 5), res.first));
    TEST_DO(equal_range(Range(5, 9), res.second));
}

TEST_MT_F("require that invalid ranges are ignored", 10, MatchLoopCommunicator(num_threads, 5)) {
    RangePair res = f1.rangeCover(makeRanges(thread_id));
    TEST_DO(equal_range(Range(1, 5), res.first));
    TEST_DO(equal_range(Range(5, 9), res.second));
}

TEST_MT_F("require that only invalid ranges produce default invalid range", 3, MatchLoopCommunicator(num_threads, 5)) {
    RangePair res = f1.rangeCover(makeRanges(10));
    Range expect;
    TEST_DO(equal_range(expect, res.first));
    TEST_DO(equal_range(expect, res.second));
}

TEST_F("require that hits dropped due to lack of diversity affects range cover result",
       MatchLoopCommunicator(num_threads, 3, std::make_unique<EveryOdd>()))
{
    TEST_DO(equal(3u, make_box<Hit>({1, 5}, {3, 3}, {5, 1}), selectBest(f1, make_box<Hit>({1, 5}, {2, 4}, {3, 3}, {4, 2}, {5, 1}))));
    // best dropped: 4
    std::vector<RangePair> input = {
        std::make_pair(Range(), Range()),
        std::make_pair(Range(3, 5), Range(1, 10)),
        std::make_pair(Range(5, 10), Range(1, 10)),
        std::make_pair(Range(1, 3), Range(1, 10))
    };
    std::vector<RangePair> expect = {
        std::make_pair(Range(), Range()),
        std::make_pair(Range(4, 5), Range(1, 10)),
        std::make_pair(Range(5, 10), Range(1, 10)),
        std::make_pair(Range(4, 4), Range(1, 10))
    };
    ASSERT_EQUAL(input.size(), expect.size());
    for (size_t i = 0; i < input.size(); ++i) {
        auto output = f1.rangeCover(input[i]);
        TEST_STATE(vespalib::make_string("case: %zu", i).c_str());
        TEST_DO(equal_range(expect[i].first, output.first));
        TEST_DO(equal_range(expect[i].second, output.second));
    }
}

TEST_MT_F("require that count_matches will count hits and docs across threads", 4, MatchLoopCommunicator(num_threads, 5)) {
    double freq = (0.0/10.0 + 1.0/11.0 + 2.0/12.0 + 3.0/13.0) / 4.0;
    EXPECT_APPROX(freq, f1.estimate_match_frequency(Matches(thread_id, thread_id + 10)), 0.00001);
}

TEST_MAIN() { TEST_RUN_ALL(); }
