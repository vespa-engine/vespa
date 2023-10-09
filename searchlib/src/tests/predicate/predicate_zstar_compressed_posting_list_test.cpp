// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_zstar_compressed_posting_list.

#include <vespa/searchlib/predicate/predicate_zstar_compressed_posting_list.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("predicate_zstar_compressed_posting_list_test");

using namespace search;
using namespace search::predicate;
using std::vector;

namespace {

struct DummyDocIdLimitProvider : public DocIdLimitProvider {
    virtual uint32_t getDocIdLimit() const override { return 10000; }
    virtual uint32_t getCommittedDocIdLimit() const override { return 10000; }
};

vespalib::GenerationHandler generation_handler;
vespalib::GenerationHolder generation_holder;
DummyDocIdLimitProvider limit_provider;
SimpleIndexConfig config;
const uint64_t hash = 0x123;

TEST("require that empty posting list starts at 0.") {
    PredicateIndex index(generation_holder, limit_provider, config, 8);
    vespalib::datastore::EntryRef ref;
    PredicateZstarCompressedPostingList<PredicateIndex::BTreeIterator>
        posting_list(index.getIntervalStore(), index.getIntervalIndex().getBTreePostingList(ref));
    EXPECT_EQUAL(0u, posting_list.getDocId());
    EXPECT_EQUAL(0u, posting_list.getInterval());
    EXPECT_FALSE(posting_list.next(0));
}

TEST("require that posting list can iterate.") {
    PredicateIndex index(generation_holder, limit_provider, config, 8);
    const auto &interval_index = index.getIntervalIndex();
    vector<vector<Interval>> intervals =
        {{{0x00010000}},
         {{0x00010000}, {0x0000ffff}},
         {{0x00010000}, {0x00000003}, {0x00040003}, {0x00060005}}};
    for (size_t i = 0; i < intervals.size(); ++i) {
        PredicateTreeAnnotations annotations(1);
        annotations.interval_map[hash] = intervals[i];
        index.indexDocument(i + 1, annotations);
    }
    index.commit();
    auto it = interval_index.lookup(hash);
    ASSERT_TRUE(it.valid());
    auto ref = it.getData();

    PredicateZstarCompressedPostingList<PredicateIndex::BTreeIterator>
        posting_list(index.getIntervalStore(), interval_index.getBTreePostingList(ref));
    EXPECT_EQUAL(0u, posting_list.getDocId());
    EXPECT_EQUAL(0u, posting_list.getInterval());

    EXPECT_TRUE(posting_list.next(0));
    EXPECT_EQUAL(1u, posting_list.getDocId());
    EXPECT_EQUAL(0x00010000u, posting_list.getInterval());
    ASSERT_TRUE(posting_list.nextInterval());
    EXPECT_EQUAL(0x00020001u, posting_list.getInterval());
    ASSERT_FALSE(posting_list.nextInterval());

    EXPECT_TRUE(posting_list.next(1));
    EXPECT_EQUAL(2u, posting_list.getDocId());
    EXPECT_EQUAL(0x00010000u, posting_list.getInterval());
    ASSERT_TRUE(posting_list.nextInterval());
    EXPECT_EQUAL(0xffff0001u, posting_list.getInterval());
    ASSERT_FALSE(posting_list.nextInterval());

    ASSERT_TRUE(posting_list.next(2));
    EXPECT_EQUAL(3u, posting_list.getDocId());
    EXPECT_EQUAL(0x00010000u, posting_list.getInterval());
    ASSERT_TRUE(posting_list.nextInterval());
    EXPECT_EQUAL(0x00030001u, posting_list.getInterval());
    ASSERT_TRUE(posting_list.nextInterval());
    EXPECT_EQUAL(0x00040003u, posting_list.getInterval());
    ASSERT_TRUE(posting_list.nextInterval());
    EXPECT_EQUAL(0x00050004u, posting_list.getInterval());
    ASSERT_TRUE(posting_list.nextInterval());
    EXPECT_EQUAL(0x00060005u, posting_list.getInterval());
    ASSERT_TRUE(posting_list.nextInterval());
    EXPECT_EQUAL(0x00070006u, posting_list.getInterval());
    ASSERT_FALSE(posting_list.nextInterval());

    ASSERT_FALSE(posting_list.next(4));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
