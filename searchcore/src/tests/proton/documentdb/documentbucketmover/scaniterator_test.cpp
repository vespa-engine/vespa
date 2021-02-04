// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover_common.h"
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("document_bucket_mover_test");

using namespace proton;
using namespace proton::move::test;
using document::BucketId;

using ScanItr = bucketdb::ScanIterator;
using ScanPass = ScanItr::Pass;

struct ScanFixtureBase
{
    test::UserDocumentsBuilder _builder;
    std::shared_ptr<BucketDBOwner> _bucketDB;
    MySubDb                    _ready;
    MySubDb                    _notReady;
    ScanFixtureBase();
    ~ScanFixtureBase();

    ScanItr getItr() {
        return ScanItr(_bucketDB->takeGuard(), BucketId());
    }

    ScanItr getItr(BucketId bucket, BucketId endBucket = BucketId(), ScanPass pass = ScanPass::FIRST) {
        return ScanItr(_bucketDB->takeGuard(), pass, bucket, endBucket);
    }
};

ScanFixtureBase::ScanFixtureBase()
    : _builder(),
      _bucketDB(std::make_shared<BucketDBOwner>()),
      _ready(_builder.getRepo(), _bucketDB, 1, SubDbType::READY),
      _notReady(_builder.getRepo(), _bucketDB, 2, SubDbType::NOTREADY)
{}
ScanFixtureBase::~ScanFixtureBase() = default;

struct ScanFixture : public ScanFixtureBase
{
    ScanFixture() : ScanFixtureBase()
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

struct OnlyNotReadyScanFixture : public ScanFixtureBase
{
    OnlyNotReadyScanFixture() : ScanFixtureBase()
    {
        _builder.createDocs(2, 1, 2);
        _builder.createDocs(4, 2, 3);
        _notReady.insertDocs(_builder.getDocs());
    }
};

struct OnlyReadyScanFixture : public ScanFixtureBase
{
    OnlyReadyScanFixture() : ScanFixtureBase()
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
        EXPECT_EQUAL(exp[i], itr.getBucket());
        ++itr;
    }
    advanceToFirstBucketWithDocs(itr, subDbType);
    EXPECT_FALSE(itr.valid());
}

TEST_F("require that we can iterate all buckets from start to end", ScanFixture)
{
    {
        ScanItr itr = f.getItr();
        assertEquals(BucketVector().
                     add(f._notReady.bucket(2)).
                     add(f._notReady.bucket(4)), itr, SubDbType::NOTREADY);
    }
    {
        ScanItr itr = f.getItr();
        assertEquals(BucketVector().
                     add(f._ready.bucket(6)).
                     add(f._ready.bucket(8)), itr, SubDbType::READY);
    }
}

TEST_F("require that we can iterate from the middle of not ready buckets", ScanFixture)
{
    BucketId bucket = f._notReady.bucket(2);
    {
        ScanItr itr = f.getItr(bucket, bucket, ScanPass::FIRST);
        assertEquals(BucketVector().
                     add(f._notReady.bucket(4)), itr, SubDbType::NOTREADY);
    }
    {
        ScanItr itr = f.getItr(BucketId(), bucket, ScanPass::SECOND);
        assertEquals(BucketVector().
                     add(f._notReady.bucket(2)), itr, SubDbType::NOTREADY);
    }
    {
        ScanItr itr = f.getItr();
        assertEquals(BucketVector().
                     add(f._ready.bucket(6)).
                     add(f._ready.bucket(8)), itr, SubDbType::READY);
    }
}

TEST_F("require that we can iterate from the middle of ready buckets", ScanFixture)
{
    BucketId bucket = f._ready.bucket(6);
    {
        ScanItr itr = f.getItr();
        assertEquals(BucketVector().
                     add(f._notReady.bucket(2)).
                     add(f._notReady.bucket(4)), itr, SubDbType::NOTREADY);
    }
    {
        ScanItr itr = f.getItr(bucket, bucket, ScanPass::FIRST);
        assertEquals(BucketVector().
                     add(f._ready.bucket(8)), itr, SubDbType::READY);
    }
    {
        ScanItr itr = f.getItr(BucketId(), bucket, ScanPass::SECOND);
        assertEquals(BucketVector().
                     add(f._ready.bucket(6)), itr, SubDbType::READY);
    }
}

TEST_F("require that we can iterate only not ready buckets", OnlyNotReadyScanFixture)
{
    ScanItr itr = f.getItr();
    assertEquals(BucketVector().
                 add(f._notReady.bucket(2)).
                 add(f._notReady.bucket(4)), itr, SubDbType::NOTREADY);
}

TEST_F("require that we can iterate only ready buckets", OnlyReadyScanFixture)
{
    ScanItr itr = f.getItr();
    assertEquals(BucketVector().
                 add(f._ready.bucket(6)).
                 add(f._ready.bucket(8)), itr, SubDbType::READY);
}

TEST_F("require that we can iterate zero buckets", ScanFixtureBase)
{
    ScanItr itr = f.getItr();
    EXPECT_FALSE(itr.valid());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
