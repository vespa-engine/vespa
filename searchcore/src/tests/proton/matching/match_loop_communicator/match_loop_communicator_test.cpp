// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/matching/match_loop_communicator.h>
#include <vespa/searchlib/features/first_phase_rank_lookup.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <algorithm>
#include <atomic>

using namespace proton::matching;

using Range = MatchLoopCommunicator::Range;
using RangePair = MatchLoopCommunicator::RangePair;
using Matches = MatchLoopCommunicator::Matches;
using Hit = MatchLoopCommunicator::Hit;
using Hits = MatchLoopCommunicator::Hits;
using TaggedHit = MatchLoopCommunicator::TaggedHit;
using TaggedHits = MatchLoopCommunicator::TaggedHits;
using search::features::FirstPhaseRankLookup;
using search::queryeval::SortedHitSequence;
using vespalib::test::Nexus;

namespace search::queryeval {

void PrintTo(const Scores& scores, std::ostream* os) {
    *os << "{" << scores.low << "," << scores.high << "}";
}

}

std::vector<Hit> hit_vec(std::vector<Hit> list) { return list; }

auto do_nothing = []() noexcept {};

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

Hits make_first_scores(size_t id, size_t size) {
    auto result = makeScores(id);
    EXPECT_LE(size, result.size());
    result.resize(size);
    return result;
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

struct EveryOdd : public search::queryeval::IDiversifier {
    bool accepted(uint32_t docId) override {
        return docId & 0x01;
    }
};

struct None : public search::queryeval::IDiversifier {
    bool accepted(uint32_t) override { return false; }
};

TEST(MatchLoopCommunicatorTest, require_that_selectBest_gives_appropriate_results_for_single_thread)
{
    constexpr size_t num_threads = 1;
    constexpr size_t thread_id = 0;
    MatchLoopCommunicator f1(num_threads, 3);
    EXPECT_EQ(hit_vec({{1, 5}, {2, 4}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}}), thread_id));
    EXPECT_EQ(hit_vec({{1, 5}, {2, 4}, {3, 3}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}, {3, 3}}), thread_id));
    EXPECT_EQ(hit_vec({{1, 5}, {2, 4}, {3, 3}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}, {3, 3}, {4, 2}}), thread_id));
}

TEST(MatchLoopCommunicatorTest, require_that_selectBest_gives_appropriate_results_for_single_thread_with_filter)
{
    constexpr size_t num_threads = 1;
    constexpr size_t thread_id = 0;
    MatchLoopCommunicator f1(num_threads, 3, std::make_unique<EveryOdd>(), nullptr, do_nothing);
    EXPECT_EQ(hit_vec({{1, 5}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}}), thread_id));
    EXPECT_EQ(hit_vec({{1, 5}, {3, 3}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}, {3, 3}}), thread_id));
    EXPECT_EQ(hit_vec({{1, 5}, {3, 3}, {5, 1}}), selectBest(f1, hit_vec({{1, 5}, {2, 4}, {3, 3}, {4, 2}, {5, 1}, {6, 0}}), thread_id));
}

TEST(MatchLoopCommunicatorTest, require_that_selectBest_works_with_no_hits)
{
    constexpr size_t num_threads = 10;
    MatchLoopCommunicator f1(num_threads, 10);
    auto task = [&f1](Nexus& ctx) {
                    EXPECT_TRUE(selectBest(f1, hit_vec({}), ctx.thread_id()).empty());
                };
    Nexus::run(num_threads, task);
}

TEST(MatchLoopCommunicatorTest, require_that_selectBest_works_with_too_many_hits_from_all_threads)
{
    constexpr size_t num_threads = 5;
    MatchLoopCommunicator f1(num_threads, 13);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    if (thread_id < 3) {
                        EXPECT_EQ(make_first_scores(thread_id, 3), selectBest(f1, makeScores(thread_id), thread_id));
                    } else {
                        EXPECT_EQ(make_first_scores(thread_id, 2), selectBest(f1, makeScores(thread_id), thread_id));
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(MatchLoopCommunicatorTest, require_that_selectBest_works_with_some_exhausted_threads)
{
    constexpr size_t num_threads = 5;
    MatchLoopCommunicator f1(num_threads, 22);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    if (thread_id < 2) {
                        EXPECT_EQ(makeScores(thread_id), selectBest(f1, makeScores(thread_id), thread_id));
                    } else {
                        EXPECT_EQ(make_first_scores(thread_id, 4), selectBest(f1, makeScores(thread_id), thread_id));
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(MatchLoopCommunicatorTest, require_that_selectBest_can_select_all_hits_from_all_threads)
{
    constexpr size_t num_threads = 5;
    MatchLoopCommunicator f1(num_threads, 100);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    EXPECT_EQ(5u, selectBest(f1, makeScores(thread_id), thread_id).size());
                };
    Nexus::run(num_threads, task);
}

TEST(MatchLoopCommunicatorTest, require_that_selectBest_works_with_some_empty_threads)
{
    constexpr size_t num_threads = 5;
    MatchLoopCommunicator f1(num_threads, 7);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    if (thread_id < 2) {
                        EXPECT_EQ(make_first_scores(thread_id, 2), selectBest(f1, makeScores(thread_id), thread_id));
                    } else if (thread_id < 5) {
                        EXPECT_EQ(make_first_scores(thread_id, 1), selectBest(f1, makeScores(thread_id), thread_id));
                    } else {
                        EXPECT_TRUE(selectBest(f1, makeScores(thread_id), thread_id).empty());
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(MatchLoopCommunicatorTest, require_that_rangeCover_works_with_a_single_thread)
{
    constexpr size_t num_threads = 1;
    constexpr size_t thread_id = 0;
    MatchLoopCommunicator f1(num_threads, 5);
    RangePair res = rangeCover(f1, hit_vec({{1, 7.5}, {2, 1.5}}), thread_id, 10);
    EXPECT_EQ(RangePair({1.5, 7.5}, {11.5, 17.5}), res);
}

TEST(MatchLoopCommunicatorTest, require_that_rangeCover_works_with_multiple_threads)
{
    constexpr size_t num_threads = 5;
    MatchLoopCommunicator f1(num_threads, 10);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    RangePair res = rangeCover(f1, hit_vec({{thread_id * 100 + 1, 100.0 + thread_id}, {thread_id * 100 + 2, 100.0 - thread_id}}), thread_id, 10);
                    EXPECT_EQ(RangePair({96.0, 104.0}, {106.0, 114.0}), res);
                };
    Nexus::run(num_threads, task);
}

TEST(MatchLoopCommunicatorTest, require_that_rangeCover_works_with_no_hits)
{
    constexpr size_t num_threads = 10;
    MatchLoopCommunicator f1(num_threads, 5);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    RangePair res = rangeCover(f1, hit_vec({}), thread_id, 10);
                    EXPECT_EQ(RangePair({}, {}), res);
                };
    Nexus::run(num_threads, task);
}

TEST(MatchLoopCommunicatorTest, require_that_hits_dropped_due_to_lack_of_diversity_affects_range_cover_result)
{
    constexpr size_t num_threads = 1;
    constexpr size_t thread_id = 0;
    MatchLoopCommunicator f1(num_threads, 3);
    MatchLoopCommunicator f2(num_threads, 3, std::make_unique<EveryOdd>(), nullptr, do_nothing);
    MatchLoopCommunicator f3(num_threads, 3, std::make_unique<None>(), nullptr, do_nothing);
    auto hits_in = hit_vec({{1, 5}, {2, 4}, {3, 3}, {4, 2}, {5, 1}});
    auto [my_work1, hits1, ranges1] = second_phase(f1, hits_in, thread_id, 10);
    auto [my_work2, hits2, ranges2] = second_phase(f2, hits_in, thread_id, 10);
    auto [my_work3, hits3, ranges3] = second_phase(f3, hits_in, thread_id, 10);

    EXPECT_EQ(my_work1, 3u);
    EXPECT_EQ(my_work2, 3u);
    EXPECT_EQ(my_work3, 0u);

    EXPECT_EQ(hit_vec({{1, 15}, {2, 14}, {3, 13}}), hits1);
    EXPECT_EQ(hit_vec({{1, 15}, {3, 13}, {5, 11}}), hits2);
    EXPECT_EQ(hit_vec({}), hits3);

    EXPECT_EQ(RangePair({3,5},{13,15}), ranges1);
    EXPECT_EQ(RangePair({4,5},{11,15}), ranges2); // best dropped: 4

    // note that the 'drops all hits due to diversity' case will
    // trigger much of the same code path as dropping second phase
    // ranking due to hard doom.

    EXPECT_EQ(RangePair({},{}), ranges3);
}

TEST(MatchLoopCommunicatorTest, require_that_estimate_match_frequency_will_count_hits_and_docs_across_threads)
{
    constexpr size_t num_threads = 4;
    MatchLoopCommunicator f1(num_threads, 5);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    double freq = (0.0/10.0 + 1.0/11.0 + 2.0/12.0 + 3.0/13.0) / 4.0;
                    EXPECT_NEAR(freq, f1.estimate_match_frequency(Matches(thread_id, thread_id + 10)), 0.00001);
                };
    Nexus::run(num_threads, task);
}

TEST(MatchLoopCommunicatorTest, require_that_second_phase_work_is_evenly_distributed_among_search_threads)
{
    constexpr size_t num_threads = 5;
    MatchLoopCommunicator f1(num_threads, 20);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    size_t num_hits = thread_id * 5;
                    size_t docid = thread_id * 100;
                    double score = thread_id * 100.0;
                    Hits my_hits;
                    for(size_t i = 0; i < num_hits; ++i) {
                        my_hits.emplace_back(++docid, score);
                        score -= 1.0;
                    }
                    auto [my_work, best_hits, ranges] = second_phase(f1, my_hits, thread_id, 1000.0);
                    EXPECT_EQ(my_work, 4u);
                    EXPECT_EQ(RangePair({381,400},{1381,1400}), ranges);
                    if (thread_id == 4) {
                        for (auto &hit: my_hits) {
                            hit.second += 1000.0;
                        }
                        EXPECT_EQ(my_hits, best_hits);
                    } else {
                        EXPECT_TRUE(best_hits.empty());
                    }
                };
    Nexus::run(num_threads, task);
}

namespace {

std::vector<double> extract_ranks(const FirstPhaseRankLookup& l) {
    std::vector<double> result;
    for (uint32_t docid = 21; docid < 26; ++docid) {
        result.emplace_back(l.lookup(docid));
    }
    return result;
}

search::feature_t unranked = std::numeric_limits<search::feature_t>::max();

using FeatureVec = std::vector<search::feature_t>;

}

TEST(MatchLoopCommunicatorTest, require_that_first_phase_rank_lookup_is_populated)
{
    constexpr size_t num_threads = 1;
    constexpr size_t thread_id = 0;
    FirstPhaseRankLookup l1;
    FirstPhaseRankLookup l2;
    MatchLoopCommunicator f1(num_threads, 3, {}, &l1, do_nothing);
    MatchLoopCommunicator f2(num_threads, 3, std::make_unique<EveryOdd>(), &l2, do_nothing);
    auto hits_in = hit_vec({{21, 5}, {22, 4}, {23, 3}, {24, 2}, {25, 1}});
    auto res1 = second_phase(f1, hits_in, thread_id, 10);
    auto res2 = second_phase(f2, hits_in, thread_id, 10);
    EXPECT_EQ(FeatureVec({1, 2, 3, unranked, unranked}), extract_ranks(l1));
    EXPECT_EQ(FeatureVec({1, unranked, 3, unranked, 5}), extract_ranks(l2));
}

TEST(MatchLoopCommunicatorTest, require_that_before_second_phase_is_called_once)
{
    constexpr size_t num_threads = 5;
    std::atomic<int> cnt(0);
    auto before_second_phase = [&cnt]() noexcept { ++cnt; };
    MatchLoopCommunicator f1(num_threads, 3, {}, nullptr, before_second_phase);
    auto task = [&f1](Nexus& ctx) {
                    auto thread_id = ctx.thread_id();
                    auto hits_in = hit_vec({});
                    (void) second_phase(f1, hits_in, thread_id, 1000.0);
                };
    Nexus::run(num_threads, task);
    EXPECT_EQ(1, cnt.load(std::memory_order_acquire));
}

GTEST_MAIN_RUN_ALL_TESTS()
