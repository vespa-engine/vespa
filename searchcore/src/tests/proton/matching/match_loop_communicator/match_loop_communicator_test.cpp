// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchcore/proton/matching/match_loop_communicator.h>
#include <algorithm>

using namespace proton::matching;

using Range = MatchLoopCommunicator::Range;
using RangePair = MatchLoopCommunicator::RangePair;
using Matches = MatchLoopCommunicator::Matches;
using Hit = MatchLoopCommunicator::Hit;
using Hits = MatchLoopCommunicator::Hits;
using TaggedHit = MatchLoopCommunicator::TaggedHit;
using TaggedHits = MatchLoopCommunicator::TaggedHits;
using search::queryeval::SortedHitSequence;

std::vector<Hit> hit_vec(std::vector<Hit> list) { return list; }

Hits makeScores(size_t id) {
    switch (id) {
    case 0: return {{1, 5.4}, {2, 4.4}, {3, 3.4}, {4, 2.4}, {5, 1.4}};
    case 1: return {{11, 5.3}, {12, 4.3}, {13, 3.3}, {14, 2.3}, {15, 1.3}};
    case 2: return {{21, 5.2}, {22, 4.2}, {23, 3.2}, {24, 2.2}, {25, 1.2}};
    case 3: return {{31, 5.1}, {32, 4.1}, {33, 3.1}, {34, 2.1}, {35, 1.1}};
    case 4: return {{41, 5.0}, {42, 4.0}, {43, 3.0}, {44, 2.0}, {45, 1.0}};
    }
    return {};
}

std::tuple<size_t,Hits,RangePair> second_phase(MatchLoopCommunicator &com, const Hits &hits, size_t thread_id, double delta = 0.0) {
    std::vector<uint32_t> refs;
    for (size_t i = 0; i < hits.size(); ++i) {
        refs.push_back(i);
    }
    auto my_work = com.get_second_phase_work(SortedHitSequence(hits.data(), refs.data(), refs.size()), thread_id);
    // the DocumentScorer used by the match thread will sort on docid here to ensure increasing seek order, this is not needed here
    size_t work_size = my_work.size();
    for (auto &[hit, tag]: my_work) {
        hit.second += delta; // second phase ranking is first phase + delta
    }
    auto [best_hits, ranges] = com.complete_second_phase(std::move(my_work), thread_id);
    // the HitCollector will sort on docid to prepare for result merging, we do it to simplify comparing with expected results
    auto sort_on_docid = [](const auto &a, const auto &b){ return (a.first < b.first); };
    std::sort(best_hits.begin(), best_hits.end(), sort_on_docid);
    return {work_size, best_hits, ranges};
}

Hits selectBest(MatchLoopCommunicator &com, const Hits &hits, size_t thread_id) {
    auto [work_size, best_hits, ranges] = second_phase(com, hits, thread_id);
    return best_hits;
}

RangePair rangeCover(MatchLoopCommunicator &com, const Hits &hits, size_t thread_id, double delta) {
    auto [work_size, best_hits, ranges] = second_phase(com, hits, thread_id, delta);
    return ranges;
}

size_t my_work_size(MatchLoopCommunicator &com, const Hits &hits, size_t thread_id) {
    auto [work_size, best_hits, ranges] = second_phase(com, hits, thread_id);
    return work_size;
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

void equal_ranges(const RangePair &a, const RangePair &b) {
    TEST_DO(equal_range(a.first, b.first));
    TEST_DO(equal_range(a.second, b.second));
}

struct EveryOdd : public search::queryeval::IDiversifier {
    bool accepted(uint32_t docId) override {
        return docId & 0x01;
    }
};

struct None : public search::queryeval::IDiversifier {
    bool accepted(uint32_t) override { return false; }
};

TEST_F("require that selectBest gives appropriate results for single thread", MatchLoopCommunicator(num_threads, 3)) {
    TEST_DO(equal(2u, hit_vec({{1, 5}, {2, 4}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}}), thread_id)));
    TEST_DO(equal(3u, hit_vec({{1, 5}, {2, 4}, {3, 3}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}, {3, 3}}), thread_id)));
    TEST_DO(equal(3u, hit_vec({{1, 5}, {2, 4}, {3, 3}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}, {3, 3}, {4, 2}}), thread_id)));
}

TEST_F("require that selectBest gives appropriate results for single thread with filter",
       MatchLoopCommunicator(num_threads, 3, std::make_unique<EveryOdd>()))
{
    TEST_DO(equal(1u, hit_vec({{1, 5}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}}), thread_id)));
    TEST_DO(equal(2u, hit_vec({{1, 5}, {3, 3}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}, {3, 3}}), thread_id)));
    TEST_DO(equal(3u, hit_vec({{1, 5}, {3, 3}, {5, 1}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}, {3, 3}, {4, 2}, {5, 1}, {6, 0}}), thread_id)));
}

TEST_MT_F("require that selectBest works with no hits", 10, MatchLoopCommunicator(num_threads, 10)) {
    EXPECT_TRUE(selectBest(f1, hit_vec({}), thread_id).empty());
}

TEST_MT_F("require that selectBest works with too many hits from all threads", 5, MatchLoopCommunicator(num_threads, 13)) {
    if (thread_id < 3) {
        TEST_DO(equal(3u, makeScores(thread_id), selectBest(f1, makeScores(thread_id), thread_id)));
    } else {
        TEST_DO(equal(2u, makeScores(thread_id), selectBest(f1, makeScores(thread_id), thread_id)));
    }
}

TEST_MT_F("require that selectBest works with some exhausted threads", 5, MatchLoopCommunicator(num_threads, 22)) {
    if (thread_id < 2) {
        TEST_DO(equal(5u, makeScores(thread_id), selectBest(f1, makeScores(thread_id), thread_id)));
    } else {
        TEST_DO(equal(4u, makeScores(thread_id), selectBest(f1, makeScores(thread_id), thread_id)));
    }
}

TEST_MT_F("require that selectBest can select all hits from all threads", 5, MatchLoopCommunicator(num_threads, 100)) {
    EXPECT_EQUAL(5u, selectBest(f1, makeScores(thread_id), thread_id).size());
}

TEST_MT_F("require that selectBest works with some empty threads", 10, MatchLoopCommunicator(num_threads, 7)) {
    if (thread_id < 2) {
        TEST_DO(equal(2u, makeScores(thread_id), selectBest(f1, makeScores(thread_id), thread_id)));
    } else if (thread_id < 5) {
        TEST_DO(equal(1u, makeScores(thread_id), selectBest(f1, makeScores(thread_id), thread_id)));
    } else {
        EXPECT_TRUE(selectBest(f1, makeScores(thread_id), thread_id).empty());
    }
}

TEST_F("require that rangeCover works with a single thread", MatchLoopCommunicator(num_threads, 5)) {
    RangePair res = rangeCover(f1, hit_vec({{1, 7.5}, {2, 1.5}}), thread_id, 10);
    TEST_DO(equal_ranges(RangePair({1.5, 7.5}, {11.5, 17.5}), res));
}

TEST_MT_F("require that rangeCover works with multiple threads", 5, MatchLoopCommunicator(num_threads, 10)) {
    RangePair res = rangeCover(f1, hit_vec({{thread_id * 100 + 1, 100.0 + thread_id}, {thread_id * 100 + 2, 100.0 - thread_id}}), thread_id, 10);
    TEST_DO(equal_ranges(RangePair({96.0, 104.0}, {106.0, 114.0}), res));
}

TEST_MT_F("require that rangeCover works with no hits", 10, MatchLoopCommunicator(num_threads, 5)) {
    RangePair res = rangeCover(f1, hit_vec({}), thread_id, 10);
    TEST_DO(equal_ranges(RangePair({}, {}), res));
}

TEST_FFF("require that hits dropped due to lack of diversity affects range cover result",
         MatchLoopCommunicator(num_threads, 3),
         MatchLoopCommunicator(num_threads, 3, std::make_unique<EveryOdd>()),
         MatchLoopCommunicator(num_threads, 3, std::make_unique<None>()))
{
    auto hits_in = hit_vec({{1, 5}, {2, 4}, {3, 3}, {4, 2}, {5, 1}});
    auto [my_work1, hits1, ranges1] = second_phase(f1, hits_in, thread_id, 10);
    auto [my_work2, hits2, ranges2] = second_phase(f2, hits_in, thread_id, 10);
    auto [my_work3, hits3, ranges3] = second_phase(f3, hits_in, thread_id, 10);

    EXPECT_EQUAL(my_work1, 3u);
    EXPECT_EQUAL(my_work2, 3u);
    EXPECT_EQUAL(my_work3, 0u);

    TEST_DO(equal(3u, hit_vec({{1, 15}, {2, 14}, {3, 13}}), hits1));
    TEST_DO(equal(3u, hit_vec({{1, 15}, {3, 13}, {5, 11}}), hits2));
    TEST_DO(equal(0u, hit_vec({}), hits3));

    TEST_DO(equal_ranges(RangePair({3,5},{13,15}), ranges1));
    TEST_DO(equal_ranges(RangePair({4,5},{11,15}), ranges2)); // best dropped: 4

    // note that the 'drops all hits due to diversity' case will
    // trigger much of the same code path as dropping second phase
    // ranking due to hard doom.

    TEST_DO(equal_ranges(RangePair({},{}), ranges3));
}

TEST_MT_F("require that estimate_match_frequency will count hits and docs across threads", 4, MatchLoopCommunicator(num_threads, 5)) {
    double freq = (0.0/10.0 + 1.0/11.0 + 2.0/12.0 + 3.0/13.0) / 4.0;
    EXPECT_APPROX(freq, f1.estimate_match_frequency(Matches(thread_id, thread_id + 10)), 0.00001);
}

TEST_MT_F("require that second phase work is evenly distributed among search threads", 5, MatchLoopCommunicator(num_threads, 20)) {
    size_t num_hits = thread_id * 5;
    size_t docid = thread_id * 100;
    double score = thread_id * 100.0;
    Hits my_hits;
    for(size_t i = 0; i < num_hits; ++i) {
        my_hits.emplace_back(++docid, score);
        score -= 1.0;
    }
    auto [my_work, best_hits, ranges] = second_phase(f1, my_hits, thread_id, 1000.0);
    EXPECT_EQUAL(my_work, 4u);
    TEST_DO(equal_ranges(RangePair({381,400},{1381,1400}), ranges));
    if (thread_id == 4) {
        for (auto &hit: my_hits) {
            hit.second += 1000.0;
        }
        TEST_DO(equal(num_hits, my_hits, best_hits));
    } else {
        EXPECT_TRUE(best_hits.empty());
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
