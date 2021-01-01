// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_search.

#include <vespa/log/log.h>
LOG_SETUP("predicate_search_test");

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/queryeval/predicate_search.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/arraysize.h>

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
    size_t _index;
    uint32_t _interval;

    void setInterval(uint32_t interval) { _interval = interval; }

public:
    MyPostingList(const vector<pair<uint32_t, uint32_t>> &entries)
        : _entries(entries),
          _index(0),
          _interval(0)
    {
    }
    MyPostingList(std::initializer_list<pair<uint32_t, uint32_t>> ilist)
        : _entries(ilist.begin(), ilist.end()),
          _index(0),
          _interval(0)
    {
    }

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
        if (_index + 1 < _entries.size() &&
            _entries[_index].first == _entries[_index + 1].first) {
            ++_index;
            setInterval(_entries[_index].second);
            return true;
        }
        return false;
    }
    uint32_t getInterval() const override { return _interval; }
};

MyPostingList::~MyPostingList() = default;

template <int N>
vector<PredicatePostingList::UP>
make_posting_lists_vector(MyPostingList (&plists)[N]) {
    vector<PredicatePostingList::UP> posting_lists;
    for (int i = 0; i < N; ++i) {
        posting_lists.emplace_back(std::make_unique<MyPostingList>(plists[i]));
    }
    return posting_lists;
}

TermFieldMatchDataArray tfmda;
typedef std::vector<uint8_t> CV;
typedef std::vector<uint8_t> MF;
typedef std::vector<uint16_t> IR;

TEST("Require that the skipping is efficient") {
    const uint8_t min_feature[] = { 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
                                    7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7};
    const uint8_t kv[] = { 6,7,6,7,6,7,6,8,6,5,6,7,6,0,6,7,
                           7,6,7,6,6,6,6,7,7,7,8,7,8,7,7,7,6,7};
    SkipMinFeature::UP skip = SkipMinFeature::create(min_feature, kv, 34);
    EXPECT_EQUAL(1u, skip->next());
    EXPECT_EQUAL(3u, skip->next());
    EXPECT_EQUAL(5u, skip->next());
    EXPECT_EQUAL(7u, skip->next());
    EXPECT_EQUAL(11u, skip->next());
    EXPECT_EQUAL(15u, skip->next());
    EXPECT_EQUAL(16u, skip->next());
    EXPECT_EQUAL(18u, skip->next());
    EXPECT_EQUAL(23u, skip->next());
    EXPECT_EQUAL(24u, skip->next());
    EXPECT_EQUAL(25u, skip->next());
    EXPECT_EQUAL(26u, skip->next());
    EXPECT_EQUAL(27u, skip->next());
    EXPECT_EQUAL(28u, skip->next());
    EXPECT_EQUAL(29u, skip->next());
    EXPECT_EQUAL(30u, skip->next());
    EXPECT_EQUAL(31u, skip->next());
    EXPECT_EQUAL(33u, skip->next());
}

TEST("require that empty search yields no results") {
    vector<PredicatePostingList::UP> posting_lists;
    MF mf(3); CV cv(3); IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, std::move(posting_lists), tfmda);
    search.initFullRange();
    EXPECT_EQUAL(SearchIterator::beginId(), search.getDocId());
    EXPECT_FALSE(search.seek(2));
    EXPECT_TRUE(search.isAtEnd());
}

TEST("require that simple search yields result") {
    MyPostingList plists[] = {{{2, 0x0001ffff}}};
    MF mf{0, 0, 0};
    CV cv{0, 0, 1};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_EQUAL(SearchIterator::beginId(), search.getDocId());
    EXPECT_FALSE(search.seek(1));
    EXPECT_EQUAL(2u, search.getDocId());
    EXPECT_TRUE(search.seek(2));
    EXPECT_EQUAL(2u, search.getDocId());
    EXPECT_FALSE(search.seek(3));
    EXPECT_TRUE(search.isAtEnd());
}

TEST("require that minFeature (K) is used to prune results") {
    MyPostingList plists[] = {{{2, 0x0001ffff}},
                              {{5, 0x0001ffff}}};
    MF mf{0, 0, 3, 0, 0, 0};
    CV cv{1, 0, 0, 0, 0, 1};
    IR ir(6, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(2));
    EXPECT_EQUAL(5u, search.getDocId());
}

TEST("require that a high K (min_feature - 1) can yield results") {
    MyPostingList plists[] = {{{2, 0x00010001}},
                              {{2, 0x0002ffff}}};
    MF mf{0, 0, 2};
    CV cv{0, 0, 2};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST("require that we can skip past entries") {
    MyPostingList plists[] = {{{2, 0x0001ffff},
                               {5, 0x0001ffff}}};
    MF mf{0, 0, 0, 0, 0, 0};
    CV cv{0, 0, 1, 0, 0, 1};
    IR ir(6, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(5));
}

TEST("require that posting lists are sorted after advancing") {
    MyPostingList plists[] = {{{1, 0x0001ffff},
                               {5, 0x0001ffff}},
                              {{2, 0x0001ffff},
                               {4, 0x0001ffff}}};
    MF mf{0, 2, 0, 0, 0, 0};
    CV cv{0, 1, 1, 0, 1, 1};
    IR ir(6, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(1));
    EXPECT_FALSE(search.seek(3));
    EXPECT_TRUE(search.seek(4));
}

TEST("require that short interval ranges works") {
    MyPostingList plists[] = {{{1, 0x00010001},
                               {5, 0x00010001}},
                              {{2, 0x00010001},
                               {4, 0x00010001}}};
    MF mf{0, 2, 0, 0, 0, 0};
    CV cv{0, 1, 1, 0, 1, 1};
    IR ir(6, 0x0001);
    PredicateSearch search(&mf[0], &ir[0], 0x1, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(1));
    EXPECT_FALSE(search.seek(3));
    EXPECT_TRUE(search.seek(4));
}

TEST("require that empty posting lists work") {
    MyPostingList plists[] = {{}};
    MF mf(3); CV cv(3); IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_EQUAL(SearchIterator::beginId(), search.getDocId());
    EXPECT_FALSE(search.seek(2));
    EXPECT_TRUE(search.isAtEnd());
}

TEST("require that shorter posting list ending is ok") {
    MyPostingList plists[] = {{{1, 0x0001ffff},
                               {2, 0x0001ffff}},
                              {{4, 0x0001ffff}}};
    MF mf{0, 0, 0, 0, 0};
    CV cv{0, 1, 1, 0, 1};
    IR ir(5, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(1));
    EXPECT_TRUE(search.seek(4));
}

TEST("require that sorting works for many posting lists") {
    MyPostingList plists[] = {{{1, 0x0001ffff},
                               {2, 0x0001ffff}},
                              {{2, 0x0001ffff},
                               {4, 0x0001ffff}},
                              {{2, 0x0001ffff},
                               {5, 0x0001ffff}},
                              {{2, 0x0001ffff},
                               {4, 0x0001ffff}},
                              {{2, 0x0001ffff},
                               {5, 0x0001ffff}}};
    MF mf{0, 1, 5, 0, 2, 2};
    CV cv{0, 1, 5, 0, 2, 2};
    IR ir(6, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(1));
    EXPECT_TRUE(search.seek(2));
    EXPECT_TRUE(search.seek(4));
    EXPECT_TRUE(search.seek(5));
}

TEST("require that insufficient interval coverage prevents match") {
    MyPostingList plists[] = {{{2, 0x00010001},
                               {3, 0x0002ffff}}};
    MF mf{0, 0, 0, 0};
    CV cv{0, 0, 1, 1};
    IR ir(4, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(2));
    EXPECT_FALSE(search.seek(3));
}

TEST("require that intervals are sorted") {
    MyPostingList plists[] = {{{2, 0x00010001}},
                              {{2, 0x0003ffff}},
                              {{2, 0x00020002}}};
    MF mf{0, 0, 0};
    CV cv{0, 0, 3};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST("require that NOT is supported - no match") {
    MyPostingList plists[] = {{{2, 0x00010001}},  // [l, r]
                              {{2, 0x00010000},   // [l, r]*
                               {2, 0xffff0001}}};  // [r+1, r+1]*
    MF mf{0, 0, 0};
    CV cv{0, 0, 3};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(2));
}

TEST("require that NOT is supported - match") {
    MyPostingList plists[] = {{{2, 0x00010000},   // [l, r]*
                               {2, 0xffff0001}}};  // [r+1, r+1]*
    MF mf{0, 0, 0};
    CV cv{0, 0, 2};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST("require that NOT is supported - no match because of previous term") {
    MyPostingList plists[] = {{{2, 0x00020001},   // [l, r]*
                               {2, 0xffff0002}}};  // [r+1, r+1]*
    MF mf{0, 0, 0};
    CV cv{0, 0, 2};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_FALSE(search.seek(2));
}

TEST("require that NOT is supported - subqueries") {
    MyPostingList plists[] = {{{2, 0x00010001}},  // [l, r]
                              {{2, 0x00010000},   // [l, r]*
                               {2, 0xffff0001}}};  // [r+1, r+1]*
    plists[0].setSubquery(0xffff);
    MF mf{0, 0, 0};
    CV cv{0, 0, 3};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST("require that there can be many intervals") {
    MyPostingList plists[] = {{{2, 0x00010001},
                               {2, 0x00020002},
                               {2, 0x00030003},
                               {2, 0x0001ffff},
                               {2, 0x00040004},
                               {2, 0x00050005},
                               {2, 0x00060006}}};
    MF mf{0, 0, 0};
    CV cv{0, 0, 7};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST("require that match can require multiple postinglists.") {
    MyPostingList plists[] = {{{2, 0x00010001}},
                              {{2, 0x0002000b},
                               {2, 0x00030003}},
                              {{2, 0x00040003}},
                              {{2, 0x00050004}},
                              {{2, 0x00010008},
                               {2, 0x00060006}},
                              {{2, 0x00020002},
                               {2, 0x0007ffff}}};
    MF mf{0, 0, 0};
    CV cv{0, 0, 9};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), tfmda);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
}

TEST("require that subquery bitmap is unpacked to subqueries.") {
    MyPostingList plists[] = {{{2, 0x0001ffff}}};
    TermFieldMatchDataArray array;
    TermFieldMatchData data;
    array.add(&data);
    MF mf{0, 0, 0};
    CV cv{0, 0, 1};
    IR ir(3, 0xffff);
    PredicateSearch search(&mf[0], &ir[0], 0xffff, cv, make_posting_lists_vector(plists), array);
    search.initFullRange();
    EXPECT_TRUE(search.seek(2));
    search.unpack(2);
    EXPECT_EQUAL(0xffffffffffffffffULL,
                 static_cast<unsigned long long>(data.getSubqueries()));
}


}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
