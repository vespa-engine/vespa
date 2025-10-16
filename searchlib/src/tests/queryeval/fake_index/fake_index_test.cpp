// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/queryeval/fake_index.h>
#include <vespa/searchlib/query/streaming/hit.h>

using namespace search::queryeval;

TEST(FakeIndexTest, require_that_basic_fake_index_works) {
    FakeIndex index;
    index.doc(69).elem(0, "..A..B..")
                 .elem(1, ".C...D..");

    auto a_result = index.lookup('A');
    auto expected_a = FakeResult().doc(69).elem(0).len(8).pos(2);
    EXPECT_EQ(a_result, expected_a);

    auto b_result = index.lookup('B');
    auto expected_b = FakeResult().doc(69).elem(0).len(8).pos(5);
    EXPECT_EQ(b_result, expected_b);

    auto c_result = index.lookup('C');
    auto expected_c = FakeResult().doc(69).elem(1).len(8).pos(1);
    EXPECT_EQ(c_result, expected_c);

    auto d_result = index.lookup('D');
    auto expected_d = FakeResult().doc(69).elem(1).len(8).pos(5);
    EXPECT_EQ(d_result, expected_d);
}

TEST(FakeIndexTest, require_that_multiple_documents_work) {
    FakeIndex index;
    index.doc(10).elem(0, "A.B")
         .doc(20).elem(0, "..A");

    auto a_result = index.lookup('A');
    auto expected_a = FakeResult().doc(10).elem(0).len(3).pos(0)
                                  .doc(20).elem(0).len(3).pos(2);
    EXPECT_EQ(a_result, expected_a);

    auto b_result = index.lookup('B');
    auto expected_b = FakeResult().doc(10).elem(0).len(3).pos(2);
    EXPECT_EQ(b_result, expected_b);
}

TEST(FakeIndexTest, require_that_multiple_occurrences_in_same_element_work) {
    FakeIndex index;
    index.doc(69).elem(0, "A.A.A");

    auto a_result = index.lookup('A');
    auto expected_a = FakeResult().doc(69).elem(0).len(5).pos(0).pos(2).pos(4);
    EXPECT_EQ(a_result, expected_a);
}

TEST(FakeIndexTest, require_that_empty_lookup_returns_empty_result) {
    FakeIndex index;
    index.doc(69).elem(0, "..A..B..");

    auto z_result = index.lookup('Z');
    EXPECT_EQ(z_result, FakeResult());
}

TEST(FakeIndexTest, require_that_dots_are_skipped) {
    FakeIndex index;
    index.doc(69).elem(0, "......");

    // No terms should be registered
    auto result = index.lookup('.');
    EXPECT_EQ(result, FakeResult());
}

TEST(FakeIndexTest, require_that_multi_field_works) {
    FakeIndex index;
    index.doc(69).field(0).elem(0, "A.B")
                 .field(1).elem(0, "..A");

    auto a0_result = index.lookup('A', 0);
    auto expected_a0 = FakeResult().doc(69).elem(0).len(3).pos(0);
    EXPECT_EQ(a0_result, expected_a0);

    auto a1_result = index.lookup('A', 1);
    auto expected_a1 = FakeResult().doc(69).elem(0).len(3).pos(2);
    EXPECT_EQ(a1_result, expected_a1);

    auto b1_result = index.lookup('B', 1);
    EXPECT_EQ(b1_result, FakeResult());
}

TEST(FakeIndexTest, require_that_streaming_hits_work) {
    FakeIndex index;
    index.doc(69).field(0).elem(1, "A.B")
                 .field(1).elem(2, "..A");

    auto hits = index.get_streaming_hits('A', 69);
    EXPECT_EQ(hits.size(), 2u);
    EXPECT_EQ(hits[0].field_id(), 0u);
    EXPECT_EQ(hits[0].element_id(), 1u);
    EXPECT_EQ(hits[0].position(), 0u);
    EXPECT_EQ(hits[1].field_id(), 1u);
    EXPECT_EQ(hits[1].element_id(), 2u);
    EXPECT_EQ(hits[1].position(), 2u);
}

TEST(FakeIndexTest, require_that_streaming_hits_with_field_filter_work) {
    FakeIndex index;
    index.doc(69).field(0).elem(0, "A.B")
                 .field(1).elem(0, "A.C");

    auto hits = index.get_streaming_hits('A', 69, std::vector<uint32_t>{1});
    EXPECT_EQ(hits.size(), 1u);
    EXPECT_EQ(hits[0].field_id(), 1u);
}

GTEST_MAIN_RUN_ALL_TESTS()
