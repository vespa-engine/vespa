// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover_common.h"
#include <vespa/searchcore/proton/bucketdb/bucketscaniterator.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("document_bucket_mover_test");

using namespace proton;
using namespace proton::move::test;
using document::BucketId;

using ScanItr = bucketdb::ScanIterator;
using ScanPass = ScanItr::Pass;

struct ScanTestBase : public ::testing::Test
{
    test::UserDocumentsBuilder _builder;
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    MySubDb                    _ready;
    MySubDb                    _notReady;
    ScanTestBase();
    ~ScanTestBase();

    ScanItr getItr() {
        return ScanItr(_bucketDB->takeGuard(), BucketId());
    }

    ScanItr getItr(BucketId bucket, BucketId endBucket = BucketId(), ScanPass pass = ScanPass::FIRST) {
        return ScanItr(_bucketDB->takeGuard(), pass, bucket, endBucket);
    }
};

ScanTestBase::ScanTestBase()
    : _builder(),
      _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
      _ready(_builder.getRepo(), _bucketDB, 1, SubDbType::READY),
      _notReady(_builder.getRepo(), _bucketDB, 2, SubDbType::NOTREADY)
{}
ScanTestBase::~ScanTestBase() = default;

struct ScanTest : public ScanTestBase
{
    ScanTest() : ScanTestBase()
    {
        _builder.createDocs(6, 1, 2);
        _builder.createDocs(8, 2, 3);
        _ready.insertDocs(_builder.getDocs());
        _builder.clearDocs();
        _builder.createDocs(2, 1, 2);
        _builder.createDocs(4, 2, 3);
        _notReady.insertDocs(_builder.getDocs());
        _builder.clearDocs();
    }
};

struct OnlyNotReadyScanTest : public ScanTestBase
{
    OnlyNotReadyScanTest() : ScanTestBase()
    {
        _builder.createDocs(2, 1, 2);
        _builder.createDocs(4, 2, 3);
        _notReady.insertDocs(_builder.getDocs());
    }
};

struct OnlyReadyScanTest : public ScanTestBase
{
    OnlyReadyScanTest() : ScanTestBase()
    {
        _builder.createDocs(6, 1, 2);
        _builder.createDocs(8, 2, 3);
        _ready.insertDocs(_builder.getDocs());
    }
};

struct BucketVector : public BucketId::List
{
    BucketVector() : BucketId::List() {}
    BucketVector &add(const BucketId &bucket) {
        push_back(bucket);
        return *this;
    }
};

void
advanceToFirstBucketWithDocs(ScanItr &itr, SubDbType subDbType)
{
    while (itr.valid()) {
        if (subDbType == SubDbType::READY) {
            if (itr.hasReadyBucketDocs())
                return;
        } else {
            if (itr.hasNotReadyBucketDocs())
                return;
        }
        ++itr;
    }
}

void assertEquals(const BucketVector &exp, ScanItr &itr, SubDbType subDbType)
{
    for (size_t i = 0; i < exp.size(); ++i) {
        advanceToFirstBucketWithDocs(itr, subDbType);
        EXPECT_TRUE(itr.valid());
        EXPECT_EQ(exp[i], itr.getBucket());
        ++itr;
    }
    advanceToFirstBucketWithDocs(itr, subDbType);
    EXPECT_FALSE(itr.valid());
}

TEST_F(ScanTest, require_that_we_can_iterate_all_buckets_from_start_to_end)
{
    {
        ScanItr itr = getItr();
        assertEquals(BucketVector().
                     add(_notReady.bucket(2)).
                     add(_notReady.bucket(4)), itr, SubDbType::NOTREADY);
    }
    {
        ScanItr itr = getItr();
        assertEquals(BucketVector().
                     add(_ready.bucket(6)).
                     add(_ready.bucket(8)), itr, SubDbType::READY);
    }
}

TEST_F(ScanTest, require_that_we_can_iterate_from_the_middle_of_not_ready_buckets)
{
    BucketId bucket = _notReady.bucket(2);
    {
        ScanItr itr = getItr(bucket, bucket, ScanPass::FIRST);
        assertEquals(BucketVector().
                     add(_notReady.bucket(4)), itr, SubDbType::NOTREADY);
    }
    {
        ScanItr itr = getItr(BucketId(), bucket, ScanPass::SECOND);
        assertEquals(BucketVector().
                     add(_notReady.bucket(2)), itr, SubDbType::NOTREADY);
    }
    {
        ScanItr itr = getItr();
        assertEquals(BucketVector().
                     add(_ready.bucket(6)).
                     add(_ready.bucket(8)), itr, SubDbType::READY);
    }
}

TEST_F(ScanTest, require_that_we_can_iterate_from_the_middle_of_ready_buckets)
{
    BucketId bucket = _ready.bucket(6);
    {
        ScanItr itr = getItr();
        assertEquals(BucketVector().
                     add(_notReady.bucket(2)).
                     add(_notReady.bucket(4)), itr, SubDbType::NOTREADY);
    }
    {
        ScanItr itr = getItr(bucket, bucket, ScanPass::FIRST);
        assertEquals(BucketVector().
                     add(_ready.bucket(8)), itr, SubDbType::READY);
    }
    {
        ScanItr itr = getItr(BucketId(), bucket, ScanPass::SECOND);
        assertEquals(BucketVector().
                     add(_ready.bucket(6)), itr, SubDbType::READY);
    }
}

TEST_F(OnlyNotReadyScanTest, require_that_we_can_iterate_only_not_ready_buckets)
{
    ScanItr itr = getItr();
    assertEquals(BucketVector().
                 add(_notReady.bucket(2)).
                 add(_notReady.bucket(4)), itr, SubDbType::NOTREADY);
}

TEST_F(OnlyReadyScanTest, require_that_we_can_iterate_only_ready_buckets)
{
    ScanItr itr = getItr();
    assertEquals(BucketVector().
                 add(_ready.bucket(6)).
                 add(_ready.bucket(8)), itr, SubDbType::READY);
}

TEST_F(ScanTestBase, require_that_we_can_iterate_zero_buckets)
{
    ScanItr itr = getItr();
    EXPECT_FALSE(itr.valid());
}

GTEST_MAIN_RUN_ALL_TESTS()
