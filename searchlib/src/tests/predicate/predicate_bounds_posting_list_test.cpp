// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_bounds_posting_list.

#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/predicate/predicate_tree_annotator.h>
#include <vespa/searchlib/predicate/predicate_bounds_posting_list.h>
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

TEST(PredicateBoundsPostingListTest, require_that_empty_bounds_posting_list_starts_at_0) {
    PredicateIndex index(generation_holder, limit_provider, config, 8);
    vespalib::datastore::EntryRef ref;
    PredicateBoundsPostingList<PredicateIndex::BTreeIterator>
        posting_list(index.getIntervalStore(),
                     index.getBoundsIndex().getBTreePostingList(ref), 42);
    EXPECT_EQ(0u, posting_list.getDocId());
    EXPECT_EQ(0u, posting_list.getInterval());
    EXPECT_FALSE(posting_list.next(0));
}

void checkNext(PredicateBoundsPostingList<PredicateIndex::BTreeIterator> &posting_list, uint32_t move_past,
               uint32_t doc_id, uint32_t interval_count) {
    std::ostringstream ost;
    ost << "checkNext(posting_list, " << move_past << ", " << doc_id
        << ", " << interval_count << ")";
    SCOPED_TRACE(ost.str());
    ASSERT_TRUE(posting_list.next(move_past));
    EXPECT_EQ(doc_id, posting_list.getDocId());
    for (uint32_t i = 0; i < interval_count - 1; ++i) {
        ASSERT_TRUE(posting_list.nextInterval());
    }
    ASSERT_FALSE(posting_list.nextInterval());
}

TEST(PredicateBoundsPostingListTest, require_that_bounds_posting_list_checks_bounds) {
    PredicateIndex index(generation_holder, limit_provider, config, 8);
    const auto &bounds_index = index.getBoundsIndex();
    for (uint32_t id = 1; id < 100; ++id) {
        PredicateTreeAnnotations annotations(id);
        auto &vec = annotations.bounds_map[hash];
        for (uint32_t i = 0; i <= id; ++i) {
            uint32_t bounds;
            if (id < 30) {
                bounds = 0x80000000 | i;  // diff >= i
            } else if (id < 60) {
                bounds = 0x40000000 | i;  // diff < i
            } else {
                bounds = (i << 16) | (i + 10);  // i < diff < i+10
            }
            vec.push_back(IntervalWithBounds{(i + 1) << 16 | 0xffff, bounds});
        }
        index.indexDocument(id, annotations);
    }
    index.commit();
    auto it = bounds_index.lookup(hash);
    ASSERT_TRUE(it.valid());
    auto ref = it.getData();

    PredicateBoundsPostingList<PredicateIndex::BTreeIterator>
        posting_list(index.getIntervalStore(),
                     bounds_index.getBTreePostingList(ref), 5);
    checkNext(posting_list, 0, 1, 2);  // [0..] -> [1..]
    checkNext(posting_list, 1, 2, 3);  // [0..] -> [2..]
    checkNext(posting_list, 10, 11, 6);  // [0..] -> [5..]
    checkNext(posting_list, 20, 21, 6);

    checkNext(posting_list, 30, 31, 26);  // [..5] -> [..30]
    checkNext(posting_list, 50, 51, 46);

    checkNext(posting_list, 60, 61, 6);  // [0..10] -> [5..15]


    PredicateBoundsPostingList<PredicateIndex::BTreeIterator>
        posting_list2(index.getIntervalStore(),
                      bounds_index.getBTreePostingList(ref), 40);
    checkNext(posting_list2, 0, 1, 2);
    checkNext(posting_list2, 1, 2, 3);
    checkNext(posting_list2, 20, 21, 22);  // [0..] -> [21..]

    checkNext(posting_list2, 30, 41, 1);  // skip ahead to match
    checkNext(posting_list2, 35, 41, 1);
    checkNext(posting_list2, 50, 51, 11);  // [..40] -> [..50]

    checkNext(posting_list2, 60, 61, 10);  // [31..40] -> [40..49]
}

}  // namespace
