// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <tests/common/teststorageapp.h>
#include <tests/common/testhelper.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <vespa/storage/persistence/filestorage/filestorhandler.h>
#include <vespa/storage/persistence/persistenceutil.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/document/base/testdocman.h>

namespace storage {

struct MessageKeeper : public MessageSender {
    std::vector<api::StorageMessage::SP> _msgs;

    void sendCommand(const api::StorageCommand::SP& m) override { _msgs.push_back(m); }
    void sendReply(const api::StorageReply::SP& m) override { _msgs.push_back(m); }
};

struct PersistenceTestEnvironment {
    PersistenceTestEnvironment(DiskCount numDisks, const std::string & rootOfRoot);

    document::TestDocMan _testDocMan;
    vdstestlib::DirConfig _config;
    MessageKeeper _messageKeeper;
    TestServiceLayerApp _node;
    StorageComponent _component;
    FileStorMetrics _metrics;
    std::unique_ptr<FileStorHandler> _handler;
    std::vector<std::unique_ptr<PersistenceUtil> > _diskEnvs;
};

class PersistenceTestUtils : public CppUnit::TestFixture {
private:
    std::unique_ptr<PersistenceTestEnvironment> _env;

public:
    PersistenceTestUtils();
    virtual ~PersistenceTestUtils();

    document::Document::SP schedulePut(
            uint32_t location,
            spi::Timestamp timestamp,
            uint16_t disk,
            uint32_t minSize = 0,
            uint32_t maxSize = 128);

    void setupDisks(uint32_t disks);

    void tearDown() override {
        _env.reset();
    }

    std::string dumpBucket(const document::BucketId& bid, uint16_t disk = 0);

    PersistenceUtil& getEnv(uint32_t disk = 0)
        { return *_env->_diskEnvs[disk]; }
    FileStorHandler& fsHandler() { return *_env->_handler; }
    FileStorMetrics& metrics() { return _env->_metrics; }
    MessageKeeper& messageKeeper() { return _env->_messageKeeper; }
    std::shared_ptr<const document::DocumentTypeRepo> getTypeRepo() { return _env->_component.getTypeRepo(); }
    StorageComponent& getComponent() { return _env->_component; }
    TestServiceLayerApp& getNode() { return _env->_node; }

    StorBucketDatabase::WrappedEntry getBucket(const document::BucketId& id);
    StorBucketDatabase::WrappedEntry createBucket(const document::BucketId& id);

    std::string getBucketStatus(const document::BucketId& id);

    spi::PersistenceProvider& getPersistenceProvider();

    /**
       Performs a put to the given disk.
       Returns the document that was inserted.
    */
    document::Document::SP doPutOnDisk(
            uint16_t disk,
            uint32_t location,
            spi::Timestamp timestamp,
            uint32_t minSize = 0,
            uint32_t maxSize = 128);

    document::Document::SP doPut(
            uint32_t location,
            spi::Timestamp timestamp,
            uint32_t minSize = 0,
            uint32_t maxSize = 128)
        { return doPutOnDisk(0, location, timestamp, minSize, maxSize); }

    /**
       Performs a remove to the given disk.
       Returns the new doccount if document was removed, or -1 if not found.
    */
    bool doRemoveOnDisk(
            uint16_t disk,
            const document::BucketId& bid,
            const document::DocumentId& id,
            spi::Timestamp timestamp,
            bool persistRemove);

    bool doRemove(
            const document::BucketId& bid,
            const document::DocumentId& id,
            spi::Timestamp timestamp,
            bool persistRemove) {
        return doRemoveOnDisk(0, bid, id, timestamp, persistRemove);
    }

    bool doUnrevertableRemoveOnDisk(uint16_t disk,
                                    const document::BucketId& bid,
                                    const document::DocumentId& id,
                                    spi::Timestamp timestamp);

    bool doUnrevertableRemove(const document::BucketId& bid,
                              const document::DocumentId& id,
                              spi::Timestamp timestamp)
    {
        return doUnrevertableRemoveOnDisk(0, bid, id, timestamp);
    }

    /**
     * Do a remove toward storage set up in test environment.
     *
     * @id Document to remove.
     * @disk If set, use this disk, otherwise lookup in bucket db.
     * @unrevertableRemove If set, instead of adding put, turn put to remove.
     * @usedBits Generate bucket to use from docid using this amount of bits.
     */
    void doRemove(const document::DocumentId& id, spi::Timestamp, uint16_t disk = 0xffff,
                  bool unrevertableRemove = false, uint16_t usedBits = 16);

    spi::GetResult doGetOnDisk(
            uint16_t disk,
            const document::BucketId& bucketId,
            const document::DocumentId& docId,
            bool headerOnly);

    spi::GetResult doGet(
            const document::BucketId& bucketId,
            const document::DocumentId& docId,
            bool headerOnly)
        { return doGetOnDisk(0, bucketId, docId, headerOnly); }

    std::shared_ptr<document::DocumentUpdate> createBodyUpdate(
            const document::DocumentId& id,
            const document::FieldValue& updateValue);

    std::shared_ptr<document::DocumentUpdate> createHeaderUpdate(
            const document::DocumentId& id,
            const document::FieldValue& updateValue);

    uint16_t getDiskFromBucketDatabaseIfUnset(const document::Bucket &,
                                              uint16_t disk = 0xffff);

    /**
     * Do a put toward storage set up in test environment.
     *
     * @doc Document to put. Use TestDocMan to generate easily.
     * @disk If set, use this disk, otherwise lookup in bucket db.
     * @usedBits Generate bucket to use from docid using this amount of bits.
     */
    void doPut(const document::Document::SP& doc, spi::Timestamp,
               uint16_t disk = 0xffff, uint16_t usedBits = 16);

    void doPut(const document::Document::SP& doc,
               document::BucketId bid,
               spi::Timestamp time,
               uint16_t disk = 0);

    spi::UpdateResult doUpdate(document::BucketId bid,
                               const std::shared_ptr<document::DocumentUpdate>& update,
                               spi::Timestamp time,
                               uint16_t disk = 0);

    document::Document::UP createRandomDocumentAtLocation(
                uint64_t location, uint32_t seed,
                uint32_t minDocSize, uint32_t maxDocSize);

    /**
     * Create a test bucket with various content representing most states a
     * bucket can represent. (Such that tests have a nice test bucket to use
     * that require operations to handle all the various bucket contents.
     *
     * @disk If set, use this disk, otherwise lookup in bucket db.
     */
    void createTestBucket(const document::Bucket&, uint16_t disk = 0xffff);

    /**
     * Create a new persistence thread.
     */
    std::unique_ptr<PersistenceThread> createPersistenceThread(uint32_t disk);

    /**
     * In-place modify doc so that it has no more body fields.
     */
    void clearBody(document::Document& doc);
};

class SingleDiskPersistenceTestUtils : public PersistenceTestUtils
{
public:
    void setUp() override {
        setupDisks(1);
    }
};

} // storage

