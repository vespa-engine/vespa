// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::MemFileTestUtils
 * \ingroup memfile
 *
 * \brief Utilities for unit tests of the MemFile layer.
 *
 * The memfile layer typically needs a MemFileEnvironment object that must be
 * set up. This class creates such an object to be used by unit tests. Other
 * utilities useful for only MemFile testing can be added here too.
 */

#pragma once

#include <vespa/memfilepersistence/memfile/memfilecache.h>
#include <tests/helper/testhelper.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/memfilepersistence/spi/memfilepersistenceprovider.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>

namespace storage {
namespace memfile {

struct FakeClock : public framework::Clock {
public:
    typedef std::unique_ptr<FakeClock> UP;

    framework::MicroSecTime _absoluteTime;

    FakeClock() {}

    virtual void addSecondsToTime(uint32_t nr) {
        _absoluteTime += framework::MicroSecTime(nr * uint64_t(1000000));
    }

    framework::MicroSecTime getTimeInMicros() const override {
        return _absoluteTime;
    }
    framework::MilliSecTime getTimeInMillis() const override {
        return getTimeInMicros().getMillis();
    }
    framework::SecondTime getTimeInSeconds() const override {
        return getTimeInMicros().getSeconds();
    }
    framework::MonotonicTimePoint getMonotonicTime() const override {
        return framework::MonotonicTimePoint(std::chrono::microseconds(
                getTimeInMicros().getTime()));
    }
};

struct MemFileTestEnvironment {
    MemFileTestEnvironment(uint32_t numDisks,
                           framework::ComponentRegister& reg,
                           const document::DocumentTypeRepo& repo);

    vdstestlib::DirConfig _config;
    MemFilePersistenceProvider _provider;
};

class MemFileTestUtils : public Types, public document::TestDocMan, public CppUnit::TestFixture {
private:
        // This variables are kept in test class. Instances that needs to be
        // unique per test needs to be setup in setupDisks and cleared in
        // tearDown
    document::BucketIdFactory _bucketIdFactory;
    framework::defaultimplementation::ComponentRegisterImpl::UP _componentRegister;
    FakeClock::UP _clock;
    std::unique_ptr<MemFileTestEnvironment> _env;

public:
    MemFileTestUtils();
    virtual ~MemFileTestUtils();

    void setupDisks(uint32_t disks);

    void tearDown() override{
        _env.reset();
        _componentRegister.reset();
        _clock.reset();
    }

    std::string getMemFileStatus(const document::BucketId& id, uint32_t disk = 0);

    std::string getModifiedBuckets();

    /**
       Flushes all cached data to disk and updates the bucket database accordingly.
    */
    void flush();

    FakeClock& getFakeClock() { return *_clock; }

    spi::Result flush(const document::BucketId& id, uint16_t disk = 0);

    MemFilePersistenceProvider& getPersistenceProvider();

    MemFilePtr getMemFile(const document::BucketId& id, uint16_t disk = 0);

    Environment& env();

    MemFilePersistenceThreadMetrics& getMetrics();

    MemFileTestEnvironment& getEnv() { return *_env; }

    /**
       Performs a put to the given disk.
       Returns the document that was inserted.
    */
    document::Document::SP doPutOnDisk(
            uint16_t disk,
            uint32_t location,
            Timestamp timestamp,
            uint32_t minSize = 0,
            uint32_t maxSize = 128);

    document::Document::SP doPut(
            uint32_t location,
            Timestamp timestamp,
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
            Timestamp timestamp,
            OperationHandler::RemoveType persistRemove);

    bool doRemove(
            const document::BucketId& bid,
            const document::DocumentId& id,
            Timestamp timestamp,
            OperationHandler::RemoveType persistRemove) {
        return doRemoveOnDisk(0, bid, id, timestamp, persistRemove);
    }

    bool doUnrevertableRemoveOnDisk(uint16_t disk,
                                    const document::BucketId& bid,
                                    const DocumentId& id,
                                    Timestamp timestamp);

    bool doUnrevertableRemove(const document::BucketId& bid,
                              const DocumentId& id,
                              Timestamp timestamp)
    {
        return doUnrevertableRemoveOnDisk(0, bid, id, timestamp);
    }

    virtual const document::BucketIdFactory& getBucketIdFactory() const
        { return _bucketIdFactory; }

    document::BucketIdFactory& getBucketIdFactory()
        { return _bucketIdFactory; }

    /**
     * Do a remove toward storage set up in test environment.
     *
     * @id Document to remove.
     * @disk If set, use this disk, otherwise lookup in bucket db.
     * @unrevertableRemove If set, instead of adding put, turn put to remove.
     * @usedBits Generate bucket to use from docid using this amount of bits.
     */
    void doRemove(const DocumentId& id, Timestamp, uint16_t disk,
                  bool unrevertableRemove = false, uint16_t usedBits = 16);

    spi::GetResult doGetOnDisk(
            uint16_t disk,
            const document::BucketId& bucketId,
            const document::DocumentId& docId,
            const document::FieldSet& fields);

    spi::GetResult doGet(
            const document::BucketId& bucketId,
            const document::DocumentId& docId,
            const document::FieldSet& fields)
        { return doGetOnDisk(0, bucketId, docId, fields); }

    document::DocumentUpdate::SP createBodyUpdate(
            const document::DocumentId& id,
            const document::FieldValue& updateValue);

    document::DocumentUpdate::SP createHeaderUpdate(
            const document::DocumentId& id,
            const document::FieldValue& updateValue);

    virtual const std::shared_ptr<const document::DocumentTypeRepo> getTypeRepo() const
    { return document::TestDocMan::getTypeRepoSP(); }

    /**
     * Do a put toward storage set up in test environment.
     *
     * @doc Document to put. Use TestDocMan to generate easily.
     * @disk If set, use this disk, otherwise lookup in bucket db.
     * @usedBits Generate bucket to use from docid using this amount of bits.
     */
    void doPut(const Document::SP& doc, Timestamp,
               uint16_t disk, uint16_t usedBits = 16);

    void doPut(const document::Document::SP& doc,
               document::BucketId bid,
               Timestamp time,
               uint16_t disk = 0);

    spi::UpdateResult doUpdate(document::BucketId bid,
                               const document::DocumentUpdate::SP& update,
                               Timestamp time,
                               uint16_t disk = 0);

    /**
     * Create a test bucket with various content representing most states a
     * bucket can represent. (Such that tests have a nice test bucket to use
     * that require operations to handle all the various bucket contents.
     *
     * @disk If set, use this disk, otherwise lookup in bucket db.
     */
    void createTestBucket(const BucketId&, uint16_t disk = 0xffff);

    /**
     * In-place modify doc so that it has no more body fields.
     */
    void clearBody(document::Document& doc);

    /**
     * Copy all header data from src into dest, replacing any
     * header fields it may already have there. NOTE: this will
     * also overwrite document ID, type etc!
     */
    void copyHeader(document::Document& dest,
                    const document::Document& src);

    /**
     * Copy all body data from src into dest, replacing any
     * body fields it may already have there.
     */
    void copyBody(document::Document& dest,
                  const document::Document& src);

    std::string stringifyFields(const Document& doc) const;

    struct IoErrors {
        int _afterReads;
        int _afterWrites;

        IoErrors()
            : _afterReads(0),
              _afterWrites(0)
        {
        }

        IoErrors& afterReads(int n) {
            _afterReads = n;
            return *this;
        }

        IoErrors& afterWrites(int n) {
            _afterWrites = n;
            return *this;
        }
    };

    /**
     * Replaces internal LazyFile factory so that it produces LazyFile
     * implementations that trigger I/O exceptions on read/write. Optionally,
     * can supply a parameter setting explicit bounds on how many operations
     * are allowed on a file before trigging exceptions from there on out. A
     * bound of -1 in practice means "don't fail ever" while 0 means "fail the
     * next op of that type".
     */
    void simulateIoErrorsForSubsequentlyOpenedFiles(
            const IoErrors& errs = IoErrors());

    /**
     * Replace internal LazyFile factory with the default, non-failing impl.
     */
    void unSimulateIoErrorsForSubsequentlyOpenedFiles();
};

class SingleDiskMemFileTestUtils : public MemFileTestUtils
{
public:
    void setUp() override {
        setupDisks(1);
    }
};

} // memfile
} // storage

