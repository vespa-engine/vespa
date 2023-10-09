// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_zero_constraint_posting_list.

#include <vespa/searchlib/predicate/predicate_zero_constraint_posting_list.h>
#include <vespa/searchlib/predicate/predicate_index.h>

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("predicate_zero_constraint_posting_list_test");

using namespace search;
using namespace search::predicate;

namespace {

struct DummyDocIdLimitProvider : public DocIdLimitProvider {
    virtual uint32_t getDocIdLimit() const override { return 10000; }
    virtual uint32_t getCommittedDocIdLimit() const override { return 10000; }
};

vespalib::GenerationHandler generation_handler;
vespalib::GenerationHolder generation_holder;
DummyDocIdLimitProvider limit_provider;
SimpleIndexConfig config;

TEST("require that empty posting list starts at 0.") {
    PredicateIndex index(generation_holder, limit_provider, config, 8);
    PredicateZeroConstraintPostingList posting_list(index.getZeroConstraintDocs().begin());
    EXPECT_EQUAL(0u, posting_list.getDocId());
    EXPECT_EQUAL(0x00010001u, posting_list.getInterval());
    EXPECT_FALSE(posting_list.next(0));
}

TEST("require that posting list can iterate.") {
    PredicateIndex index(generation_holder, limit_provider, config, 8);
    for (uint32_t id = 1; id < 100; ++id) {
        index.indexEmptyDocument(id);
    }
    index.commit();
    ASSERT_EQUAL(99u, index.getZeroConstraintDocs().size());

    PredicateZeroConstraintPostingList posting_list(index.getZeroConstraintDocs().begin());
    EXPECT_EQUAL(0u, posting_list.getDocId());
    EXPECT_EQUAL(0x00010001u, posting_list.getInterval());

    for (size_t i = 0; i < 99; ++i) {
        EXPECT_TRUE(posting_list.next(i));
        EXPECT_EQUAL(i + 1, posting_list.getDocId());
        EXPECT_EQUAL(0x00010001u, posting_list.getInterval());
        EXPECT_FALSE(posting_list.nextInterval());
    }
    EXPECT_FALSE(posting_list.next(99));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
