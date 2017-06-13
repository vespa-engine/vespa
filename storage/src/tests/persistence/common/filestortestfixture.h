// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vdstestlib/cppunit/macros.h>
#include <tests/common/testhelper.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storageapi/message/persistence.h>
#include <tests/common/dummystoragelink.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>

namespace storage {

class FileStorTestFixture : public CppUnit::TestFixture
{
public:
    static spi::LoadType defaultLoadType;

    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<vdstestlib::DirConfig> _config;
    std::unique_ptr<vdstestlib::DirConfig> _config2;
    std::unique_ptr<vdstestlib::DirConfig> _smallConfig;
    const document::DocumentType* _testdoctype1;

    static const uint32_t MSG_WAIT_TIME = 60 * 1000;

    typedef uint32_t DocumentIndex;
    typedef uint64_t PutTimestamp;

    void setUp() override;
    void tearDown() override;
    void setupDisks(uint32_t diskCount);
    void createBucket(const document::BucketId& bid);
    bool bucketExistsInDb(const document::BucketId& bucket) const;

    api::ReturnCode::Result resultOf(const api::StorageReply& reply) const {
        return reply.getResult().getResult();
    }
    void setClusterState(const std::string&);

    struct StorageLinkInjector
    {
        virtual ~StorageLinkInjector() {}

        virtual void inject(DummyStorageLink&) const = 0;
    };

    struct NoOpStorageLinkInjector : StorageLinkInjector
    {
        void inject(DummyStorageLink&) const override {}
    };

    void
    expectNoReplies(DummyStorageLink& link) {
        CPPUNIT_ASSERT_EQUAL(size_t(0), link.getNumReplies());
    }

    template <typename ReplyType>
    void
    expectReply(DummyStorageLink& link,
                api::ReturnCode::Result result)
    {
        link.waitForMessages(1, 60*1000);
        api::StorageReply* reply(
                dynamic_cast<ReplyType*>(link.getReply(0).get()));
        if (reply == 0) {
            std::ostringstream ss;
            ss << "got unexpected reply "
            << link.getReply(0)->toString(true);
            CPPUNIT_FAIL(ss.str());
        }
        CPPUNIT_ASSERT_EQUAL(result, reply->getResult().getResult());
    }

    template <typename ReplyType>
    void
    expectAbortedReply(DummyStorageLink& link) {
        expectReply<ReplyType>(link, api::ReturnCode::ABORTED);
    }

    template <typename ReplyType>
    void
    expectOkReply(DummyStorageLink& link) {
        expectReply<ReplyType>(link, api::ReturnCode::OK);
    }


    struct TestFileStorComponents
    {
    private:
        TestName _testName;
        FileStorTestFixture& _fixture;
    public:
        DummyStorageLink top;
        FileStorManager* manager;

        TestFileStorComponents(FileStorTestFixture& fixture,
                               const char* testName,
                               const StorageLinkInjector& i = NoOpStorageLinkInjector());

        api::StorageMessageAddress makeSelfAddress() const;

        void sendDummyGet(const document::BucketId& bid);
        void sendPut(const document::BucketId& bid,
                     uint32_t docIdx,
                     uint64_t timestamp);
        void sendDummyGetDiff(const document::BucketId& bid);
    };
};

} // ns storage
