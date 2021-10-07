// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/vdslib/container/searchresult.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace vdslib {

TEST(SearchResultTest, test_simple)
{
    SearchResult a;
    EXPECT_EQ(0, a.getHitCount());
    a.addHit(7, "doc1", 6);
    ASSERT_EQ(1, a.getHitCount());
    a.addHit(8, "doc2", 7);
    ASSERT_EQ(2, a.getHitCount());
    const char *docId;
    SearchResult::RankType r;
    EXPECT_EQ(7, a.getHit(0, docId, r));
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_EQ(6, r);
    EXPECT_EQ(8, a.getHit(1, docId, r));
    EXPECT_EQ("doc2", std::string(docId));
    EXPECT_EQ(7, r);
    a.sort();
    EXPECT_EQ(8, a.getHit(0, docId, r));
    EXPECT_EQ("doc2", std::string(docId));
    EXPECT_EQ(7, r);
    EXPECT_EQ(7, a.getHit(1, docId, r));
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_EQ(6, r);
}

TEST(SearchResultTest, test_simple_sort_data)
{
    SearchResult a;
    EXPECT_EQ(0, a.getHitCount());
    a.addHit(7, "doc1", 6, "abce", 4);
    ASSERT_EQ(1, a.getHitCount());
    a.addHit(8, "doc2", 7, "abcde", 5);
    ASSERT_EQ(2, a.getHitCount());
    const char *docId;
    SearchResult::RankType r;
    EXPECT_EQ(7, a.getHit(0, docId, r));
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_EQ(6, r);
    const void *buf;
    size_t sz;
    a.getSortBlob(0, buf, sz);
    EXPECT_EQ(4, sz);
    EXPECT_TRUE(memcmp("abce", buf, sz) == 0);
    EXPECT_EQ(8, a.getHit(1, docId, r));
    EXPECT_EQ("doc2", std::string(docId));
    EXPECT_EQ(7, r);
    a.getSortBlob(1, buf, sz);
    EXPECT_EQ(5, sz);
    EXPECT_TRUE(memcmp("abcde", buf, sz) == 0);
    a.sort();
    EXPECT_EQ(8, a.getHit(0, docId, r));
    EXPECT_EQ("doc2", std::string(docId));
    EXPECT_EQ(7, r);
    a.getSortBlob(0, buf, sz);
    EXPECT_EQ(5, sz);
    EXPECT_EQ(7, a.getHit(1, docId, r));
    EXPECT_EQ("doc1", std::string(docId));
    EXPECT_EQ(6, r);
    a.getSortBlob(1, buf, sz);
    EXPECT_EQ(4, sz);
}

}
