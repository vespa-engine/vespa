// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/vdslib/container/searchresult.h>
#include <cppunit/extensions/HelperMacros.h>

namespace vdslib {

struct SearchResultTest : public CppUnit::TestFixture {

    void testSimple();
    void testSimpleSortData();

    CPPUNIT_TEST_SUITE(SearchResultTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testSimpleSortData);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SearchResultTest);

void SearchResultTest::testSimple()
{
    SearchResult a;
    CPPUNIT_ASSERT(a.getHitCount() == 0);
    a.addHit(7, "doc1", 6);
    CPPUNIT_ASSERT(a.getHitCount() == 1);
    a.addHit(8, "doc2", 7);
    CPPUNIT_ASSERT(a.getHitCount() == 2);
    const char *docId;
    SearchResult::RankType r;
    CPPUNIT_ASSERT_EQUAL(a.getHit(0, docId, r), 7ul);
    CPPUNIT_ASSERT(strcmp(docId, "doc1") == 0);
    CPPUNIT_ASSERT(r == 6);
    CPPUNIT_ASSERT_EQUAL(a.getHit(1, docId, r), 8ul);
    CPPUNIT_ASSERT(strcmp(docId, "doc2") == 0);
    CPPUNIT_ASSERT(r == 7);
    a.sort();
    CPPUNIT_ASSERT_EQUAL(a.getHit(0, docId, r), 8ul);
    CPPUNIT_ASSERT(strcmp(docId, "doc2") == 0);
    CPPUNIT_ASSERT(r == 7);
    CPPUNIT_ASSERT_EQUAL(a.getHit(1, docId, r), 7ul);
    CPPUNIT_ASSERT(strcmp(docId, "doc1") == 0);
    CPPUNIT_ASSERT(r == 6);
}

void SearchResultTest::testSimpleSortData()
{
    SearchResult a;
    CPPUNIT_ASSERT(a.getHitCount() == 0);
    a.addHit(7, "doc1", 6, "abce", 4);
    CPPUNIT_ASSERT(a.getHitCount() == 1);
    a.addHit(8, "doc2", 7, "abcde", 5);
    CPPUNIT_ASSERT(a.getHitCount() == 2);
    const char *docId;
    SearchResult::RankType r;
    CPPUNIT_ASSERT_EQUAL(a.getHit(0, docId, r), 7ul);
    CPPUNIT_ASSERT(strcmp(docId, "doc1") == 0);
    CPPUNIT_ASSERT(r == 6);
    const void *buf;
    size_t sz;
    a.getSortBlob(0, buf, sz);
    CPPUNIT_ASSERT(sz == 4);
    CPPUNIT_ASSERT(memcmp("abce", buf, sz) == 0);
    CPPUNIT_ASSERT_EQUAL(a.getHit(1, docId, r), 8ul);
    CPPUNIT_ASSERT(strcmp(docId, "doc2") == 0);
    CPPUNIT_ASSERT(r == 7);
    a.getSortBlob(1, buf, sz);
    CPPUNIT_ASSERT(sz == 5);
    CPPUNIT_ASSERT(memcmp("abcde", buf, sz) == 0);
    a.sort();
    CPPUNIT_ASSERT_EQUAL(a.getHit(0, docId, r), 8ul);
    CPPUNIT_ASSERT(strcmp(docId, "doc2") == 0);
    CPPUNIT_ASSERT(r == 7);
    a.getSortBlob(0, buf, sz);
    CPPUNIT_ASSERT(sz == 5);
    CPPUNIT_ASSERT_EQUAL(a.getHit(1, docId, r), 7ul);
    CPPUNIT_ASSERT(strcmp(docId, "doc1") == 0);
    CPPUNIT_ASSERT(r == 6);
    a.getSortBlob(1, buf, sz);
    CPPUNIT_ASSERT(sz == 4);
}

}
