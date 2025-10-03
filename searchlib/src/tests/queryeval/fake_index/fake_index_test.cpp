// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/queryeval/fake_index.h>

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

GTEST_MAIN_RUN_ALL_TESTS()
