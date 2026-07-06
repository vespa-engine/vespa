// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/queryenvironment.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::matching::doc_count_from_docid_limit;

TEST(QueryEnvironmentTest, doc_count_from_docid_limit_clamps_empty_lid_space) {
    EXPECT_EQ(1u, doc_count_from_docid_limit(0));
    EXPECT_EQ(1u, doc_count_from_docid_limit(1));
    EXPECT_EQ(1u, doc_count_from_docid_limit(2));
    EXPECT_EQ(999u, doc_count_from_docid_limit(1000));
}

GTEST_MAIN_RUN_ALL_TESTS()
