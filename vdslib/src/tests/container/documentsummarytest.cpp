// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/vdslib/container/documentsummary.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace vdslib {

TEST(DocumentSummaryTest, test_simple)
{
    DocumentSummary a;
    EXPECT_EQ(0, a.getSummaryCount());
    a.addSummary("doc1", "summary1", 8);
    ASSERT_EQ(1, a.getSummaryCount());
    a.addSummary("aoc12", "summary17", 9);
    ASSERT_EQ(2, a.getSummaryCount());

    size_t r;
    const char * docId;
    const void * buf(nullptr);
    a.getSummary(0, docId, buf, r);
    EXPECT_EQ(8, r);
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_TRUE(memcmp(buf, "summary1", r) == 0);
    a.getSummary(1, docId, buf, r);
    EXPECT_EQ(9, r);
    EXPECT_EQ("aoc12", std::string(docId));
    EXPECT_TRUE(memcmp(buf, "summary17", r) == 0);

    a.sort();
    a.getSummary(0, docId, buf, r);
    EXPECT_EQ(9, r);
    EXPECT_EQ("aoc12", std::string(docId));
    EXPECT_TRUE(memcmp(buf, "summary17", r) == 0);
    a.getSummary(1, docId, buf, r);
    EXPECT_EQ(8, r);
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_TRUE(memcmp(buf, "summary1", r) == 0);
}

}
