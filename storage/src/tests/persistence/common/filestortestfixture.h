// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <tests/common/teststorageapp.h>
#include <vespa/storage/persistence/filestorage/filestorhandlerimpl.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage {

class FileStorTestFixture : public ::testing::Test
{
public:
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<vdstestlib::DirConfig> _config;
    const document::DocumentType* _testdoctype1;

    static const uint32_t MSG_WAIT_TIME = 60 * 1000;

    typedef uint32_t DocumentIndex;
    typedef uint64_t PutTimestamp;

    void SetUp() override;
    void TearDown() override;
    void setupPersistenceThreads(uint32_t threads);
    void createBucket(const document::BucketId& bid);
    bool bucketExistsInDb(const document::BucketId& bucket) const;

    static api::StorageMessageAddress makeSelfAddress();

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
        EXPECT_EQ(0, link.getNumReplies());
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
            FAIL() << "got unexpected reply "
                   << link.getReply(0)->toString(true);
        }
        EXPECT_EQ(result, reply->getResult().getResult());
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
        FileStorTestFixture& _fixture;
    public:
        DummyStorageLink top;
        FileStorManager* manager;

        TestFileStorComponents(FileStorTestFixture& fixture,
                               const StorageLinkInjector& i = NoOpStorageLinkInjector());

        void sendDummyGet(const document::BucketId& bid);
        void sendPut(const document::BucketId& bid,
                     uint32_t docIdx,
                     uint64_t timestamp);
        void sendDummyGetDiff(const document::BucketId& bid);
    };
};

} // ns storage
