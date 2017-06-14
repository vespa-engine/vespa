// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/bucketdatabase.h>
#include <vespa/storage/storageutil/utils.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <cppunit/extensions/HelperMacros.h>

#define SETUP_DATABASE_TESTS() \
    CPPUNIT_TEST(testUpdateGetAndRemove); \
    CPPUNIT_TEST(testClear); \
    CPPUNIT_TEST(testIterating); \
    CPPUNIT_TEST(testFindParents); \
    CPPUNIT_TEST(testFindAll); \
    CPPUNIT_TEST(testCreateAppropriateBucket); \
    CPPUNIT_TEST(testGetNext); \
    CPPUNIT_TEST(testGetNextReturnsUpperBoundBucket); \
    CPPUNIT_TEST(testUpperBoundReturnsNextInOrderGreaterBucket); \
    CPPUNIT_TEST(testChildCount);

namespace storage {
namespace distributor {

struct BucketDatabaseTest : public CppUnit::TestFixture {
    void setUp() override ;

    void testUpdateGetAndRemove();
    void testClear();
    void testIterating();
    void testFindParents();
    void testFindAll();
    void testCreateAppropriateBucket();
    void testGetNext();
    void testGetNextReturnsUpperBoundBucket();
    void testUpperBoundReturnsNextInOrderGreaterBucket();
    void testChildCount();

    void testBenchmark();

    std::string doFindParents(const std::vector<document::BucketId>& ids,
                              const document::BucketId& searchId);
    std::string doFindAll(const std::vector<document::BucketId>& ids,
                          const document::BucketId& searchId);
    document::BucketId doCreate(const std::vector<document::BucketId>& ids,
                                uint32_t minBits,
                                const document::BucketId& wantedId);

    virtual BucketDatabase& db() = 0;

private:
    using UBoundFunc = std::function<
            document::BucketId(const BucketDatabase&,
                               const document::BucketId&)>;

    void doTestUpperBound(const UBoundFunc& f);
};

}

}

