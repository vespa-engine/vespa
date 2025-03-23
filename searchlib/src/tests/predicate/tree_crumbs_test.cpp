// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for TreeCrumbs.

#include <vespa/searchlib/predicate/tree_crumbs.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::predicate;

namespace {

TEST(TreeCrumbsTest, require_that_crumbs_can_set_child_and_resize) {
    TreeCrumbs crumbs;
    EXPECT_EQ(0u, crumbs.size());
    EXPECT_EQ("", crumbs.getCrumb());
    crumbs.setChild(2);
    EXPECT_EQ(2u, crumbs.size());
    EXPECT_EQ(":2", crumbs.getCrumb());
    crumbs.setChild(12345);
    EXPECT_EQ(8u, crumbs.size());
    EXPECT_EQ(":2:12345", crumbs.getCrumb());
    crumbs.resize(2);
    EXPECT_EQ(2u, crumbs.size());
    EXPECT_EQ(":2", crumbs.getCrumb());
    crumbs.setChild(42);
    EXPECT_EQ(5u, crumbs.size());
    EXPECT_EQ(":2:42", crumbs.getCrumb());
    crumbs.resize(2);
    EXPECT_EQ(2u, crumbs.size());
    EXPECT_EQ(":2", crumbs.getCrumb());
    crumbs.resize(0);
    EXPECT_EQ(0u, crumbs.size());
    EXPECT_EQ("", crumbs.getCrumb());
}

TEST(TreeCrumbsTest, require_that_child_counts_of_max_uint32_t_is_ok) {
    TreeCrumbs crumbs;
    EXPECT_EQ(0u, crumbs.size());
    EXPECT_EQ("", crumbs.getCrumb());
    crumbs.setChild(0xffffffff);
    EXPECT_EQ(11u, crumbs.size());
    EXPECT_EQ(":4294967295", crumbs.getCrumb());
}

TEST(TreeCrumbsTest, require_that_child_0_gets_number) {
    TreeCrumbs crumbs;
    crumbs.setChild(0);
    EXPECT_EQ(2u, crumbs.size());
    EXPECT_EQ(":0", crumbs.getCrumb());
}

TEST(TreeCrumbsTest, require_that_crumbs_can_set_custom_initial_char) {
    TreeCrumbs crumbs;
    crumbs.setChild(0, 'a');
    crumbs.setChild(1, 'b');
    crumbs.setChild(2, 'c');
    EXPECT_EQ("a0b1c2", crumbs.getCrumb());
}

}  // namespace
