// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_search.

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/queryeval/predicate_search.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/arraysize.h>

#include <vespa/log/log.h>
LOG_SETUP("predicate_search_test");

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using namespace search::queryeval;
using namespace search::predicate;
using std::pair;
using std::vector;
using vespalib::arraysize;

namespace {

class MyPostingList : public PredicatePostingList {
    vector<pair<uint32_t, uint32_t>> _entries;
    size_t                           _index;
    uint32_t                         _interval;

    void setInterval(uint32_t interval) { _interval = interval; }

public:
    MyPostingList(const vector<pair<uint32_t, uint32_t>>& entries) : _entries(entries), _index(0), _interval(0) {}
    MyPostingList(std::initializer_list<pair<uint32_t, uint32_t>> ilist)
        : _entries(ilist.begin(), ilist.end()), _index(0), _interval(0) {}

    ~MyPostingList() override;

    bool next(uint32_t doc_id) override {
        if (_index < _entries.size()) {
            while (_entries[_index].first <= doc_id) {
                ++_index;
                if (_index == _entries.size()) {
                    setDocId(search::endDocId);
                    return false;
                }
            }
            setDocId(_entries[_index].first);
            setInterval(_entries[_index].second);
            return true;
        }
        setDocId(search::endDocId);
        return false;
    }

    bool nextInterval() override {
        if (_index + 1 < _entries.size() && _entries[_index].first == _entries[_index + 1].first) {
            ++_index;
            setInterval(_entries[_index].second);
            return true;
        }
        return false;
    }
    uint32_t getInterval() const override { return _interval; }
    void reset() override {
        _index = 0;
        _interval = 0;
        setDocId(0);
    }
};

MyPostingList::~MyPostingList() = default;

template <int N> vector<PredicatePostingList::UP> make_posting_lists_vector(MyPostingList (&plists)[N]) {
    vector<PredicatePostingList::UP> posting_lists;
    for (int i = 0; i < N; ++i) {
        posting_lists.emplace_back(std::make_unique<MyPostingList>(plists[i]));
    }
    return posting_lists;
}

TermFieldMatchDataArray tfmda;
using CV = std::vector<uint8_t>;
using MF = std::vector<uint8_t>;
using IR = std::vector<uint16_t>;

TEST(PredicateSearchTest, Require_that_the_skipping_is_efficient) {
    const uint8_t      min_feature[] = {7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                                        7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7};
    const uint8_t      kv[] = {6, 7, 6, 7, 6, 7, 6, 8, 6, 5, 6, 7, 6, 0, 6, 7, 7,
                               6, 7, 6, 6, 6, 6, 7, 7, 7, 8, 7, 8, 7, 7, 7, 6, 7};
    SkipMinFeature::UP skip = SkipMinFeature::create(min_feature, kv, 34);
    EXPECT_EQ(1u, skip->next());
    EXPECT_EQ(3u, skip->next());
    EXPECT_EQ(5u, skip->next());
    EXPECT_EQ(7u, skip->next());
    EXPECT_EQ(11u, skip->next());
    EXPECT_EQ(15u, skip->next());
    EXPECT_EQ(16u, skip->next());
    EXPECT_EQ(18u, skip->next());
    EXPECT_EQ(23u, skip->next());
    EXPECT_EQ(24u, skip->next());
    EXPECT_EQ(25u, skip->next());
    EXPECT_EQ(26u, skip->next());
    EXPECT_EQ(27u, skip->next());
    EXPECT_EQ(28u, skip->next());
    EXPECT_EQ(29u, skip->next());
    EXPECT_EQ(30u, skip->next());
    EXPECT_EQ(31u, skip->next());
    EXPECT_EQ(33u, skip->next());
}

TEST(PredicateSearchTest, require_that_empty_search_yields_no_results) {
    vector<PredicatePostingList::UP> posting_lists;
    MF                               mf(3);
    CV                               cv(3);
    IR                               ir(3, 0xffff);
    PredicateSearch                  search(&mf[0], &ir[0], 0xffff, cv, std::move(posting_lists), tfmda);
    search.initFullRange();
    EXPECT_EQ(SearchIterator::beginId(), search.getDocId());
    EXPECT_FALSE(search.seek(2));
    EXPECT_TRUE(search.isAtEnd());
}

TEST(PredicateSearchTest, require_that_simple_search_yields_result) {
    MyPostingList   plists[] = {{{2, 0x0001ffff}}};
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 1};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_EQ(SearchIterator::beginId(), search.getDocId());
    EXPECT_FALSE(search.seek(1));
    EXPECT_EQ(2u, search.getDocId());
    EXPECT_TRUE(search.seek(2));
    EXPECT_EQ(2u, search.getDocId());
    EXPECT_FALSE(search.seek(3));
    EXPECT_TRUE(search.isAtEnd());
}

TEST(PredicateSearchTest, require_that_minFeature_K_is_used_to_prune_results) {
    MyPostingList   plists[] = {{{2, 0x0001ffff}}, {{5, 0x0001ffff}}};
    MF              mf{0, 0, 3, 0, 0, 0};
    CV              cv{1, 0, 0, 0, 0, 1};
    IR              ir(6, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(2));
    EXPECT_EQ(5u, search.getDocId());
}

TEST(PredicateSearchTest, require_that_a_high_K_min_feature_minus_1_can_yield_results) {
    MyPostingList   plists[] = {{{2, 0x00010001}}, {{2, 0x0002ffff}}};
    MF              mf{0, 0, 2};
    CV              cv{0, 0, 2};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST(PredicateSearchTest, require_that_we_can_skip_past_entries) {
    MyPostingList   plists[] = {{{2, 0x0001ffff}, {5, 0x0001ffff}}};
    MF              mf{0, 0, 0, 0, 0, 0};
    CV              cv{0, 0, 1, 0, 0, 1};
    IR              ir(6, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(5));
}

TEST(PredicateSearchTest, require_that_posting_lists_are_sorted_after_advancing) {
    MyPostingList   plists[] = {{{1, 0x0001ffff}, {5, 0x0001ffff}}, {{2, 0x0001ffff}, {4, 0x0001ffff}}};
    MF              mf{0, 2, 0, 0, 0, 0};
    CV              cv{0, 1, 1, 0, 1, 1};
    IR              ir(6, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(1));
    EXPECT_FALSE(search.seek(3));
    EXPECT_TRUE(search.seek(4));
}

TEST(PredicateSearchTest, require_that_short_interval_ranges_works) {
    MyPostingList   plists[] = {{{1, 0x00010001}, {5, 0x00010001}}, {{2, 0x00010001}, {4, 0x00010001}}};
    MF              mf{0, 2, 0, 0, 0, 0};
    CV              cv{0, 1, 1, 0, 1, 1};
    IR              ir(6, 0x0001);
    PredicateSearch search(&mf[0], &ir[0], 0x1, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(1));
    EXPECT_FALSE(search.seek(3));
    EXPECT_TRUE(search.seek(4));
}

TEST(PredicateSearchTest, require_that_empty_posting_lists_work) {
    MyPostingList   plists[] = {{}};
    MF              mf(3);
    CV              cv(3);
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_EQ(SearchIterator::beginId(), search.getDocId());
    EXPECT_FALSE(search.seek(2));
    EXPECT_TRUE(search.isAtEnd());
}

TEST(PredicateSearchTest, require_that_shorter_posting_list_ending_is_ok) {
    MyPostingList   plists[] = {{{1, 0x0001ffff}, {2, 0x0001ffff}}, {{4, 0x0001ffff}}};
    MF              mf{0, 0, 0, 0, 0};
    CV              cv{0, 1, 1, 0, 1};
    IR              ir(5, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(1));
    EXPECT_TRUE(search.seek(4));
}

TEST(PredicateSearchTest, require_that_sorting_works_for_many_posting_lists) {
    MyPostingList   plists[] = {{{1, 0x0001ffff}, {2, 0x0001ffff}},
                                {{2, 0x0001ffff}, {4, 0x0001ffff}},
                                {{2, 0x0001ffff}, {5, 0x0001ffff}},
                                {{2, 0x0001ffff}, {4, 0x0001ffff}},
                                {{2, 0x0001ffff}, {5, 0x0001ffff}}};
    MF              mf{0, 1, 5, 0, 2, 2};
    CV              cv{0, 1, 5, 0, 2, 2};
    IR              ir(6, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(1));
    EXPECT_TRUE(search.seek(2));
    EXPECT_TRUE(search.seek(4));
    EXPECT_TRUE(search.seek(5));
}

TEST(PredicateSearchTest, require_that_insufficient_interval_coverage_prevents_match) {
    MyPostingList   plists[] = {{{2, 0x00010001}, {3, 0x0002ffff}}};
    MF              mf{0, 0, 0, 0};
    CV              cv{0, 0, 1, 1};
    IR              ir(4, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(2));
    EXPECT_FALSE(search.seek(3));
}

TEST(PredicateSearchTest, require_that_intervals_are_sorted) {
    MyPostingList   plists[] = {{{2, 0x00010001}}, {{2, 0x0003ffff}}, {{2, 0x00020002}}};
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 3};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST(PredicateSearchTest, require_that_NOT_is_supported_no_match) {
    MyPostingList   plists[] = {{{2, 0x00010001}},  // [l, r]
                                {{2, 0x00010000},   // [l, r]*
                                 {2, 0xffff0001}}}; // [r+1, r+1]*
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 3};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(2));
}

TEST(PredicateSearchTest, require_that_NOT_is_supported_match) {
    MyPostingList   plists[] = {{{2, 0x00010000},   // [l, r]*
                                 {2, 0xffff0001}}}; // [r+1, r+1]*
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 2};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST(PredicateSearchTest, require_that_NOT_is_supported_no_match_because_of_previous_term) {
    MyPostingList   plists[] = {{{2, 0x00020001},   // [l, r]*
                                 {2, 0xffff0002}}}; // [r+1, r+1]*
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 2};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(2));
}

TEST(PredicateSearchTest, require_that_NOT_is_supported_subqueries) {
    MyPostingList plists[] = {{{2, 0x00010001}},  // [l, r]
                              {{2, 0x00010000},   // [l, r]*
                               {2, 0xffff0001}}}; // [r+1, r+1]*
    plists[0].setSubquery(0xffff);
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 3};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST(PredicateSearchTest, require_that_there_can_be_many_intervals) {
    MyPostingList   plists[] = {{{2, 0x00010001},
                                 {2, 0x00020002},
                                 {2, 0x00030003},
                                 {2, 0x0001ffff},
                                 {2, 0x00040004},
                                 {2, 0x00050005},
                                 {2, 0x00060006}}};
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 7};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST(PredicateSearchTest, require_that_match_can_require_multiple_postinglists) {
    MyPostingList plists[] = {
        {{2, 0x00010001}}, {{2, 0x0002000b}, {2, 0x00030003}}, {{2, 0x00040003}},
        {{2, 0x00050004}}, {{2, 0x00010008}, {2, 0x00060006}}, {{2, 0x00020002}, {2, 0x0007ffff}}};
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 9};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST(PredicateSearchTest, require_that_subquery_bitmap_is_unpacked_to_subqueries) {
    MyPostingList           plists[] = {{{2, 0x0001ffff}}};
    TermFieldMatchDataArray array;
    TermFieldMatchData      data;
    array.add(&data);
    MF              mf{0, 0, 0};
    CV              cv{0, 0, 1};
    IR              ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), array);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
    search.unpack(2);
    EXPECT_EQ(0xffffffffffffffffUL, data.getSubqueries());
}

// Hits at docids around the skipper's 32-doc chunks: 2, 31, 32, 33, 63, 64, 65, 100.
std::vector<uint32_t> hits_in_range(PredicateSearch& search, uint32_t begin, uint32_t end) {
    std::vector<uint32_t> hits;
    search.initRange(begin, end);
    for (uint32_t doc_id = search.seekFirst(begin); doc_id < end; doc_id = search.seekNext(doc_id + 1)) {
        hits.push_back(doc_id);
    }
    return hits;
}

TEST(PredicateSearchTest, require_that_ranges_can_start_anywhere_and_rewind) {
    const std::vector<uint32_t>           docs{2, 31, 32, 33, 63, 64, 65, 100};
    std::vector<pair<uint32_t, uint32_t>> entries;
    for (uint32_t doc : docs) {
        entries.emplace_back(doc, 0x0001ffff);
    }
    MF                               mf(110, 0);
    CV                               cv(110, 1);
    IR                               ir(110, 0xffff);
    vector<PredicatePostingList::UP> posting_lists;
    posting_lists.emplace_back(std::make_unique<MyPostingList>(entries));
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, std::move(posting_lists), tfmda);
    EXPECT_EQ(docs, hits_in_range(search, 1, 110));
    EXPECT_EQ((std::vector<uint32_t>{32, 33, 63, 64}), hits_in_range(search, 32, 65));
    EXPECT_EQ((std::vector<uint32_t>{2, 31}), hits_in_range(search, 1, 32)); // rewinds
    EXPECT_EQ((std::vector<uint32_t>{64, 65, 100}), hits_in_range(search, 64, 110));
    EXPECT_EQ((std::vector<uint32_t>{33}), hits_in_range(search, 33, 34)); // rewinds
    EXPECT_EQ((std::vector<uint32_t>{65, 100}), hits_in_range(search, 65, 110));
    EXPECT_EQ((std::vector<uint32_t>{}), hits_in_range(search, 101, 110));
    EXPECT_EQ(docs, hits_in_range(search, 1, 110)); // rewinds
}

TEST(PredicateSearchTest, require_that_rewind_restores_exhausted_posting_lists_and_subqueries) {
    MyPostingList plists[] = {
        {{10, 0x0001ffff}, {40, 0x0001ffff}},
        {{10, 0x0001ffff}, {40, 0x00010001}, {40, 0x0002ffff}, {70, 0x0001ffff}, {100, 0x0001ffff}}};
    auto posting_lists = make_posting_lists_vector(plists);
    posting_lists[0]->setSubquery(0x1);
    posting_lists[1]->setSubquery(0x2);
    TermFieldMatchDataArray array;
    TermFieldMatchData      data;
    array.add(&data);
    MF              mf(110, 0);
    CV              cv(110, 1);
    IR              ir(110, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, std::move(posting_lists), array);
    using Hits = std::vector<pair<uint32_t, uint64_t>>;
    auto hits_in_range = [&](uint32_t begin, uint32_t end) {
        Hits hits;
        search.initRange(begin, end);
        for (uint32_t doc_id = search.seekFirst(begin); doc_id < end; doc_id = search.seekNext(doc_id + 1)) {
            search.unpack(doc_id);
            hits.emplace_back(doc_id, data.getSubqueries());
        }
        return hits;
    };
    const Hits all{{10, 0x3}, {40, 0x3}, {70, 0x2}, {100, 0x2}};
    EXPECT_EQ(all, hits_in_range(1, 110));                         // the first list runs out at 40
    EXPECT_EQ((Hits{{10, 0x3}, {40, 0x3}}), hits_in_range(1, 41)); // rewinds, and ends right after evaluating 40
    EXPECT_EQ((Hits{{40, 0x3}}), hits_in_range(40, 50));           // rewinds, as the intervals at 40 are used up
    EXPECT_EQ((Hits{{70, 0x2}, {100, 0x2}}), hits_in_range(41, 110));
    EXPECT_EQ(all, hits_in_range(1, 110)); // rewinds
}

class Verifier : public search::test::SearchIteratorVerifier {
    // Two posting lists taking every other expected hit, so one runs out first.
    vector<pair<uint32_t, uint32_t>> _entries[2];
    MF                               _mf;
    mutable CV                       _cv;
    IR                               _ir;

public:
    Verifier() : _mf(getDocIdLimit(), 0), _cv(getDocIdLimit(), 1), _ir(getDocIdLimit(), 0xffff) {
        const auto& docs = getExpectedDocIds();
        for (size_t i = 0; i < docs.size(); ++i) {
            _entries[i % 2].emplace_back(docs[i], 0x0001ffff);
        }
    }
    ~Verifier() override;
    SearchIterator::UP create(bool) const override {
        vector<PredicatePostingList::UP> posting_lists;
        for (const auto& entries : _entries) {
            posting_lists.emplace_back(std::make_unique<MyPostingList>(entries));
        }
        return std::make_unique<PredicateSearch>(&_mf[0], &_ir[0], 0xffff, _cv, std::move(posting_lists), tfmda);
    }
};

Verifier::~Verifier() = default;

TEST(PredicateSearchTest, require_that_search_iterator_verifier_passes) {
    Verifier verifier;
    verifier.verify();
}

} // namespace

GTEST_MAIN_RUN_ALL_TESTS()
