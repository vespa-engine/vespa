// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <vespa/vdslib/container/smallvector.h>
#include <sys/time.h>

namespace storage {
namespace lib {

struct IndexedContainerIteratorTest : public CppUnit::TestFixture {

    void testNormalUsage();
    void testSorting();

    CPPUNIT_TEST_SUITE(IndexedContainerIteratorTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST(testSorting);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(IndexedContainerIteratorTest);

void
IndexedContainerIteratorTest::testNormalUsage()
{
    typedef IndexedContainerIterator<std::vector<int>, int> Iterator;
    {
        std::vector<int> v;
        Iterator begin = Iterator(v, 0);
        Iterator end = Iterator(v, 0);
        CPPUNIT_ASSERT_EQUAL(begin, Iterator(v, 0));
        CPPUNIT_ASSERT_EQUAL(end, Iterator(v, 0));
        CPPUNIT_ASSERT(begin == end);
    }
    {
        std::vector<int> v;
        v.push_back(5);
        Iterator begin = Iterator(v, 0);
        Iterator end = Iterator(v, 1);
        CPPUNIT_ASSERT_EQUAL(begin, Iterator(v, 0));
        CPPUNIT_ASSERT_EQUAL(end, Iterator(v, 1));
        CPPUNIT_ASSERT(begin != end);
    }
}

void
IndexedContainerIteratorTest::testSorting()
{
    typedef IndexedContainerIterator<std::vector<int>, int> Iterator;
    std::vector<int> v;
    v.push_back(5);
    v.push_back(9);
    v.push_back(2);
    std::sort(Iterator(v, 0), Iterator(v, 3));
    CPPUNIT_ASSERT_EQUAL(2, v[0]);
    CPPUNIT_ASSERT_EQUAL(5, v[1]);
    CPPUNIT_ASSERT_EQUAL(9, v[2]);
}

} // lib
} // storage
