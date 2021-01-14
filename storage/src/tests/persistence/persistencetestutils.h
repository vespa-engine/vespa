// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <vespa/storage/persistence/filestorage/filestorhandler.h>
#include <vespa/storage/persistence/persistenceutil.h>
#include <vespa/storage/persistence/bucketownershipnotifier.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage {

struct MessageKeeper : public MessageSender {
    std::vector<std::shared_ptr<api::StorageMessage>> _msgs;

    void sendCommand(const std::shared_ptr<api::StorageCommand> & m) override { _msgs.push_back(m); }
    void sendReply(const std::shared_ptr<api::StorageReply> & m) override { _msgs.push_back(m); }
};

struct PersistenceTestEnvironment {
    PersistenceTestEnvironment(const std::string & rootOfRoot);
    ~PersistenceTestEnvironment();

    document::TestDocMan _testDocMan;
    vdstestlib::DirConfig _config;
    MessageKeeper _messageKeeper;
    TestServiceLayerApp _node;
    ServiceLayerComponent _component;
    FileStorMetrics _metrics;
    std::unique_ptr<FileStorHandler> _handler;
    std::unique_ptr<PersistenceUtil>  _diskEnv;
};

class PersistenceTestUtils : public testing::Test {
public:
    class NoBucketLock : public FileStorHandler::BucketLockInterface
    {
    public:
        NoBucketLock(document::Bucket bucket) noexcept : _bucket(bucket) { }
        const document::Bucket &getBucket() const override {
            return _bucket;
        }
        api::LockingRequirements lockingRequirements() const noexcept override {
            return api::LockingRequirements::Shared;
        }
        static std::shared_ptr<NoBucketLock> make(document::Bucket bucket) {
            return std::make_shared<NoBucketLock>(bucket);
        }
    private:
        document::Bucket _bucket;
    };

    struct ReplySender : public MessageSender {
        void sendCommand(const std::shared_ptr<api::StorageCommand> &) override {
            abort();
        }

        void sendReply(const std::shared_ptr<api::StorageReply> & ptr) override {
            queue.enqueue(std::move(ptr));
        }

        Queue queue;
    };

    std::unique_ptr<PersistenceTestEnvironment> _env;
    std::unique_ptr<vespalib::ISequencedTaskExecutor> _sequenceTaskExecutor;
    ReplySender _replySender;
    BucketOwnershipNotifier _bucketOwnershipNotifier;
    std::unique_ptr<PersistenceHandler> _persistenceHandler;

    PersistenceTestUtils();
    ~PersistenceTestUtils() override;

    document::Document::SP schedulePut(uint32_t location, spi::Timestamp timestamp, uint32_t minSize = 0, uint32_t maxSize = 128);

    void setupDisks();
    void setupExecutor(uint32_t numThreads);

    void TearDown() override {
        if (_sequenceTaskExecutor) {
            _sequenceTaskExecutor->sync();
            _sequenceTaskExecutor.reset();
        }
        _env.reset();
    }

    std::string dumpBucket(const document::BucketId& bid);

    PersistenceUtil& getEnv() { return *_env->_diskEnv; }
    FileStorHandler& fsHandler() { return *_env->_handler; }
    FileStorMetrics& metrics() { return _env->_metrics; }
    MessageKeeper& messageKeeper() { return _env->_messageKeeper; }
    std::shared_ptr<const document::DocumentTypeRepo> getTypeRepo() { return _env->_component.getTypeRepo()->documentTypeRepo; }
    StorageComponent& getComponent() { return _env->_component; }
    TestServiceLayerApp& getNode() { return _env->_node; }

    StorBucketDatabase::WrappedEntry getBucket(const document::BucketId& id);
    StorBucketDatabase::WrappedEntry createBucket(const document::BucketId& id);

    std::string getBucketStatus(const document::BucketId& id);

    spi::PersistenceProvider& getPersistenceProvider();

    MessageTracker::UP
    createTracker(api::StorageMessage::SP cmd, document::Bucket bucket) {
        return MessageTracker::createForTesting(framework::MilliSecTimer(getEnv()._component.getClock()), getEnv(),
                                                _replySender, NoBucketLock::make(bucket), std::move(cmd));
    }

    api::ReturnCode
    fetchResult(const MessageTracker::UP & tracker) {
        if (tracker) {
            return tracker->getResult();
        }
        std::shared_ptr<api::StorageMessage> msg;
        _replySender.queue.getNext(msg, 60s);
        return dynamic_cast<api::StorageReply &>(*msg).getResult();
    }

    /**
       Returns the document that was inserted.
    */
    document::Document::SP doPutOnDisk(
            uint32_t location,
            spi::Timestamp timestamp,
            uint32_t minSize = 0,
            uint32_t maxSize = 128);

    document::Document::SP doPut(
            uint32_t location,
            spi::Timestamp timestamp,
            uint32_t minSize = 0,
            uint32_t maxSize = 128)
        { return doPutOnDisk(location, timestamp, minSize, maxSize); }

    /**
       Returns the new doccount if document was removed, or -1 if not found.
    */
    bool doRemoveOnDisk(
            const document::BucketId& bid,
            const document::DocumentId& id,
            spi::Timestamp timestamp,
            bool persistRemove);

    bool doRemove(
            const document::BucketId& bid,
            const document::DocumentId& id,
            spi::Timestamp timestamp,
            bool persistRemove) {
        return doRemoveOnDisk(bid, id, timestamp, persistRemove);
    }

    bool doUnrevertableRemoveOnDisk(const document::BucketId& bid,
                                    const document::DocumentId& id,
                                    spi::Timestamp timestamp);

    bool doUnrevertableRemove(const document::BucketId& bid,
                              const document::DocumentId& id,
                              spi::Timestamp timestamp)
    {
        return doUnrevertableRemoveOnDisk(bid, id, timestamp);
    }

    /**
     * Do a remove toward storage set up in test environment.
     *
     * @id Document to remove.
     * @unrevertableRemove If set, instead of adding put, turn put to remove.
     * @usedBits Generate bucket to use from docid using this amount of bits.
     */
    void doRemove(const document::DocumentId& id, spi::Timestamp,
                  bool unrevertableRemove = false, uint16_t usedBits = 16);

    spi::GetResult doGetOnDisk(
            const document::BucketId& bucketId,
            const document::DocumentId& docId);

    spi::GetResult doGet(
            const document::BucketId& bucketId,
            const document::DocumentId& docId)
        { return doGetOnDisk(bucketId, docId); }

    std::shared_ptr<document::DocumentUpdate> createBodyUpdate(
            const document::DocumentId& id,
            const document::FieldValue& updateValue);

    std::shared_ptr<document::DocumentUpdate> createHeaderUpdate(
            const document::DocumentId& id,
            const document::FieldValue& updateValue);

    uint16_t getDiskFromBucketDatabaseIfUnset(const document::Bucket &);

    /**
     * Do a put toward storage set up in test environment.
     *
     * @doc Document to put. Use TestDocMan to generate easily.
     * @usedBits Generate bucket to use from docid using this amount of bits.
     */
    void doPut(const document::Document::SP& doc, spi::Timestamp, uint16_t usedBits = 16);

    void doPut(const document::Document::SP& doc,
               document::BucketId bid,
               spi::Timestamp time);

    spi::UpdateResult doUpdate(document::BucketId bid,
                               const std::shared_ptr<document::DocumentUpdate>& update,
                               spi::Timestamp time);

    document::Document::UP createRandomDocumentAtLocation(
                uint64_t location, uint32_t seed,
                uint32_t minDocSize, uint32_t maxDocSize);

    /**
     * Create a test bucket with various content representing most states a
     * bucket can represent. (Such that tests have a nice test bucket to use
     * that require operations to handle all the various bucket contents.
     *
     */
    void createTestBucket(const document::Bucket&);

    /**
     * In-place modify doc so that it has no more body fields.
     */
    void clearBody(document::Document& doc);
};

class SingleDiskPersistenceTestUtils : public PersistenceTestUtils
{
public:
};

} // storage

