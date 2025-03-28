// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_interval_posting_list.

#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vespa/searchlib/predicate/predicate_interval_posting_list.h>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search;
using namespace search::predicate;
namespace {

struct DummyDocIdLimitProvider : public DocIdLimitProvider {
    uint32_t getDocIdLimit() const override { return 10000; }
    uint32_t getCommittedDocIdLimit() const override { return 10000; }
};

vespalib::GenerationHandler generation_handler;
vespalib::GenerationHolder generation_holder;
DummyDocIdLimitProvider limit_provider;
SimpleIndexConfig config;
const uint64_t hash = 0x123;

TEST(PredicateIntervalPostingListTest, require_that_empty_posting_list_starts_at_0) {
    PredicateIndex index(generation_holder, limit_provider, config, 8);
    vespalib::datastore::EntryRef ref;
    PredicateIntervalPostingList<PredicateIndex::BTreeIterator>
    posting_list(index.getIntervalStore(), index.getIntervalIndex().getBTreePostingList(ref));
    EXPECT_EQ(0u, posting_list.getDocId());
    EXPECT_EQ(0u, posting_list.getInterval());
    EXPECT_FALSE(posting_list.next(0));
}

TEST(PredicateIntervalPostingListTest, require_that_posting_list_can_iterate) {
    PredicateIndex index(generation_holder, limit_provider, config, 8);
    const auto &interval_index = index.getIntervalIndex();
    for (uint32_t id = 1; id < 100; ++id) {
        PredicateTreeAnnotations annotations(id);
        auto &vec = annotations.interval_map[hash];
        for (uint32_t i = 0; i < id; ++i) {
            vec.push_back(Interval{(i + 1) << 16 | 0xffff});
        }
        index.indexDocument(id, annotations);
    }
    index.commit();
    auto it = interval_index.lookup(hash);
    ASSERT_TRUE(it.valid());
    auto ref = it.getData();

    PredicateIntervalPostingList<PredicateIndex::BTreeIterator>
        posting_list(index.getIntervalStore(), interval_index.getBTreePostingList(ref));
    EXPECT_EQ(0u, posting_list.getDocId());
    EXPECT_EQ(0u, posting_list.getInterval());
    EXPECT_TRUE(posting_list.next(0));
    EXPECT_EQ(1u, posting_list.getDocId());
    EXPECT_EQ(0x0001ffffu, posting_list.getInterval());
    ASSERT_FALSE(posting_list.nextInterval());
    ASSERT_TRUE(posting_list.next(1));
    EXPECT_EQ(2u, posting_list.getDocId());
    EXPECT_EQ(0x0001ffffu, posting_list.getInterval());
    ASSERT_TRUE(posting_list.nextInterval());
    EXPECT_EQ(0x0002ffffu, posting_list.getInterval());
    ASSERT_FALSE(posting_list.nextInterval());

    ASSERT_TRUE(posting_list.next(50));
    EXPECT_EQ(51u, posting_list.getDocId());
    for (uint32_t i = 0; i < 50; ++i) {
        EXPECT_EQ((i + 1) << 16 | 0xffff, posting_list.getInterval());
        ASSERT_TRUE(posting_list.nextInterval());
    }
    EXPECT_EQ(0x0033ffffu, posting_list.getInterval());
    ASSERT_FALSE(posting_list.nextInterval());
}

}  // namespace
