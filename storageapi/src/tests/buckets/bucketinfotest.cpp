// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage {
namespace api {

struct BucketInfo_Test : public CppUnit::TestFixture {

    void testSimple();

    CPPUNIT_TEST_SUITE(BucketInfo_Test);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(BucketInfo_Test);

/** Tests simple operations */
void BucketInfo_Test::testSimple()
{
    BucketInfo info;

    CPPUNIT_ASSERT_EQUAL(false, info.valid());
    CPPUNIT_ASSERT_EQUAL(0u, info.getChecksum());
    CPPUNIT_ASSERT_EQUAL(0u, info.getDocumentCount());
    CPPUNIT_ASSERT_EQUAL(1u, info.getTotalDocumentSize());

    info.setChecksum(0xa000bbbb);
    info.setDocumentCount(15);
    info.setTotalDocumentSize(64000);

    CPPUNIT_ASSERT_EQUAL(true, info.valid());
    CPPUNIT_ASSERT_EQUAL(0xa000bbbb, info.getChecksum());
    CPPUNIT_ASSERT_EQUAL(15u, info.getDocumentCount());
    CPPUNIT_ASSERT_EQUAL(64000u, info.getTotalDocumentSize());
};

} // api
} // storage
