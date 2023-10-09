// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for TreeCrumbs.

#include <vespa/log/log.h>
LOG_SETUP("TreeCrumbs_test");

#include <vespa/searchlib/predicate/tree_crumbs.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace search::predicate;

namespace {

TEST("require that crumbs can set child and resize") {
    TreeCrumbs crumbs;
    EXPECT_EQUAL(0u, crumbs.size());
    EXPECT_EQUAL("", crumbs.getCrumb());
    crumbs.setChild(2);
    EXPECT_EQUAL(2u, crumbs.size());
    EXPECT_EQUAL(":2", crumbs.getCrumb());
    crumbs.setChild(12345);
    EXPECT_EQUAL(8u, crumbs.size());
    EXPECT_EQUAL(":2:12345", crumbs.getCrumb());
    crumbs.resize(2);
    EXPECT_EQUAL(2u, crumbs.size());
    EXPECT_EQUAL(":2", crumbs.getCrumb());
    crumbs.setChild(42);
    EXPECT_EQUAL(5u, crumbs.size());
    EXPECT_EQUAL(":2:42", crumbs.getCrumb());
    crumbs.resize(2);
    EXPECT_EQUAL(2u, crumbs.size());
    EXPECT_EQUAL(":2", crumbs.getCrumb());
    crumbs.resize(0);
    EXPECT_EQUAL(0u, crumbs.size());
    EXPECT_EQUAL("", crumbs.getCrumb());
}

TEST("require that child counts of 2^31 - 1 is ok") {
    TreeCrumbs crumbs;
    EXPECT_EQUAL(0u, crumbs.size());
    EXPECT_EQUAL("", crumbs.getCrumb());
    crumbs.setChild(0xffffffff);
    EXPECT_EQUAL(11u, crumbs.size());
    EXPECT_EQUAL(":4294967295", crumbs.getCrumb());
}

TEST("require that child 0 gets number") {
    TreeCrumbs crumbs;
    crumbs.setChild(0);
    EXPECT_EQUAL(2u, crumbs.size());
    EXPECT_EQUAL(":0", crumbs.getCrumb());
}

TEST("require that crumbs can set custom initial char") {
    TreeCrumbs crumbs;
    crumbs.setChild(0, 'a');
    crumbs.setChild(1, 'b');
    crumbs.setChild(2, 'c');
    EXPECT_EQUAL("a0b1c2", crumbs.getCrumb());
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
