// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/vdslib/container/documentsummary.h>
#include <cppunit/extensions/HelperMacros.h>

namespace vdslib {

struct DocumentSummaryTest : public CppUnit::TestFixture {

    void testSimple();

    CPPUNIT_TEST_SUITE(DocumentSummaryTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentSummaryTest);

void DocumentSummaryTest::testSimple()
{
    DocumentSummary a;
    CPPUNIT_ASSERT(a.getSummaryCount() == 0);
    a.addSummary("doc1", "summary1", 8);
    CPPUNIT_ASSERT(a.getSummaryCount() == 1);
    a.addSummary("aoc12", "summary17", 9);
    CPPUNIT_ASSERT(a.getSummaryCount() == 2);

    size_t r;
    const char * docId;
    const void * buf(NULL);
    a.getSummary(0, docId, buf, r);
    CPPUNIT_ASSERT(r == 8);
    CPPUNIT_ASSERT(strcmp(docId, "doc1") == 0);
    CPPUNIT_ASSERT(memcmp(buf, "summary1", r) == 0);
    a.getSummary(1, docId, buf, r);
    CPPUNIT_ASSERT(r == 9);
    CPPUNIT_ASSERT(strcmp(docId, "aoc12") == 0);
    CPPUNIT_ASSERT(memcmp(buf, "summary17", r) == 0);

    a.sort();
    a.getSummary(0, docId, buf, r);
    CPPUNIT_ASSERT(r == 9);
    CPPUNIT_ASSERT(strcmp(docId, "aoc12") == 0);
    CPPUNIT_ASSERT(memcmp(buf, "summary17", r) == 0);
    a.getSummary(1, docId, buf, r);
    CPPUNIT_ASSERT(r == 8);
    CPPUNIT_ASSERT(strcmp(docId, "doc1") == 0);
    CPPUNIT_ASSERT(memcmp(buf, "summary1", r) == 0);
}

}
