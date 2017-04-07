// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/memfilepersistence/mapper/memfile_v1_serializer.h>
#include <vespa/memfilepersistence/mapper/simplememfileiobuffer.h>
#include <tests/spi/memfiletestutils.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/memfilepersistence/mapper/locationreadplanner.h>
#include <tests/spi/simulatedfailurefile.h>
#include <tests/spi/options_builder.h>

namespace storage {
namespace memfile {

struct MemFileV1SerializerTest : public SingleDiskMemFileTestUtils
{
    void tearDown() override;
    void setUpPartialWriteEnvironment();
    void resetConfig(uint32_t minimumFileSize, uint32_t minimumFileHeaderBlockSize);
    void doTestPartialWriteRemove(bool readAll);
    void doTestPartialWriteUpdate(bool readAll);

    void testWriteReadSingleDoc();
    void testWriteReadPartial();
    void testWriteReadPartialRemoved();
    void testPartialWritePutHeaderOnly();
    void testPartialWritePut();
    void testPartialWriteRemoveCached();
    void testPartialWriteRemoveNotCached();
    void testPartialWriteUpdateCached();
    void testPartialWriteUpdateNotCached();
    void testPartialWriteTooMuchFreeSpace();
    void testPartialWriteNotEnoughFreeSpace();
    void testWriteReadSingleRemovedDoc();
    void testLocationDiskIoPlannerSimple();
    void testLocationDiskIoPlannerMergeReads();
    void testLocationDiskIoPlannerAlignReads();
    void testLocationDiskIoPlannerOneDocument();
    void testSeparateReadsForHeaderAndBody();
    void testLocationsRemappedConsistently();
    void testHeaderBufferTooSmall();

    /*std::unique_ptr<MemFile> createMemFile(FileSpecification& file,
                                         bool callLoadFile)
    {
        return std::unique_ptr<MemFile>(new MemFile(file, env(), callLoadFile));
        }*/

    CPPUNIT_TEST_SUITE(MemFileV1SerializerTest);
    CPPUNIT_TEST(testWriteReadSingleDoc);
    CPPUNIT_TEST(testWriteReadPartial);
    CPPUNIT_TEST(testWriteReadPartialRemoved);
    CPPUNIT_TEST(testWriteReadSingleRemovedDoc);
    CPPUNIT_TEST(testPartialWritePutHeaderOnly);
    CPPUNIT_TEST(testPartialWritePut);
    CPPUNIT_TEST(testPartialWriteRemoveCached);
    CPPUNIT_TEST(testPartialWriteRemoveNotCached);
    CPPUNIT_TEST(testPartialWriteUpdateCached);
    CPPUNIT_TEST(testPartialWriteUpdateNotCached);
    CPPUNIT_TEST(testLocationDiskIoPlannerSimple);
    CPPUNIT_TEST(testLocationDiskIoPlannerMergeReads);
    CPPUNIT_TEST(testLocationDiskIoPlannerAlignReads);
    CPPUNIT_TEST(testLocationDiskIoPlannerOneDocument);
    CPPUNIT_TEST(testSeparateReadsForHeaderAndBody);
    CPPUNIT_TEST(testPartialWriteTooMuchFreeSpace);
    CPPUNIT_TEST(testPartialWriteNotEnoughFreeSpace);
    CPPUNIT_TEST(testLocationsRemappedConsistently);
    CPPUNIT_TEST(testHeaderBufferTooSmall);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemFileV1SerializerTest);

namespace {

const vespalib::LazyFile&
getFileHandle(const MemFile& mf1)
{
    return static_cast<const SimpleMemFileIOBuffer&>(
            mf1.getMemFileIO()).getFileHandle();
}

const LoggingLazyFile&
getLoggerFile(const MemFile& file)
{
    return static_cast<const LoggingLazyFile&>(getFileHandle(file));
}

bool isContentEqual(MemFile& mf1, MemFile& mf2,
                    bool requireEqualContentCached, std::ostream& error)
{
    MemFile::const_iterator it1(
            mf1.begin(Types::ITERATE_GID_UNIQUE | Types::ITERATE_REMOVED));
    MemFile::const_iterator it2(
            mf2.begin(Types::ITERATE_GID_UNIQUE | Types::ITERATE_REMOVED));
    while (true) {
        if (it1 == mf1.end() && it2 == mf2.end()) {
            return true;
        }
        if (it1 == mf1.end() || it2 == mf2.end()) {
            error << "Different amount of GID unique slots";
            return false;
        }
        if (it1->getTimestamp() != it2->getTimestamp()) {
            error << "Different timestamps";
            return false;
            }
        if (it1->getGlobalId() != it2->getGlobalId()) {
            error << "Different gids";
            return false;
        }
        if (it1->getPersistedFlags() != it2->getPersistedFlags()) {
            error << "Different persisted flags";
            return false;
        }
        if (requireEqualContentCached) {
            if (mf1.partAvailable(*it1, Types::BODY)
                ^ mf2.partAvailable(*it2, Types::BODY)
                || mf1.partAvailable(*it1, Types::HEADER)
                ^ mf2.partAvailable(*it2, Types::HEADER))
            {
                error << "Difference in cached content: ";
                return false;
            }
        }

        if (mf1.partAvailable(*it1, Types::HEADER) &&
            mf2.partAvailable(*it2, Types::HEADER))
        {
            document::Document::UP doc1 = mf1.getDocument(*it1, Types::ALL);
            document::Document::UP doc2 = mf2.getDocument(*it2, Types::ALL);

            CPPUNIT_ASSERT(doc1.get());
            CPPUNIT_ASSERT(doc2.get());

            if (*doc1 != *doc2) {
                error << "Documents different: Expected:\n"
                      << doc1->toString(true) << "\nActual:\n"
                      << doc2->toString(true) << "\n";
                return false;
            }
        }
        ++it1;
        ++it2;
    }
}

bool
validateMemFileStructure(const MemFile& mf, std::ostream& error)
{
    const SimpleMemFileIOBuffer& ioBuf(
            dynamic_cast<const SimpleMemFileIOBuffer&>(mf.getMemFileIO()));
    const FileInfo& fileInfo(ioBuf.getFileInfo());
    if (fileInfo.getFileSize() % 512) {
        error << "File size is not a multiple of 512 bytes";
        return false;
    }
    if (fileInfo.getBlockIndex(Types::BODY) % 512) {
        error << "Body start index is not a multiple of 512 bytes";
        return false;
    }
    if (fileInfo.getBlockSize(Types::BODY) % 512) {
        error << "Body size is not a multiple of 512 bytes";
        return false;
    }
    return true;
}

}

void
MemFileV1SerializerTest::tearDown() {
    //_memFile.reset();
}

/**
 * Adjust minimum slotfile size values to avoid rewriting file
 * when we want to get a partial write
 */
void
MemFileV1SerializerTest::setUpPartialWriteEnvironment()
{
    resetConfig(4096, 2048);
}

void
MemFileV1SerializerTest::resetConfig(uint32_t minimumFileSize,
                                     uint32_t minimumFileHeaderBlockSize)
{
    using MemFileConfig = vespa::config::storage::StorMemfilepersistenceConfig;
    using MemFileConfigBuilder
        = vespa::config::storage::StorMemfilepersistenceConfigBuilder;

    MemFileConfigBuilder persistenceConfig(
            *env().acquireConfigReadLock().memFilePersistenceConfig());
    persistenceConfig.minimumFileHeaderBlockSize = minimumFileHeaderBlockSize;
    persistenceConfig.minimumFileSize = minimumFileSize;
    auto newCfg = std::unique_ptr<MemFileConfig>(
            new MemFileConfig(persistenceConfig));
    env().acquireConfigWriteLock().setMemFilePersistenceConfig(
            std::move(newCfg));
}

struct DummyMemFileIOInterface : MemFileIOInterface {
    Document::UP getDocumentHeader(const document::DocumentTypeRepo&,
                                   DataLocation) const override
    {
        return Document::UP();
    }

    document::DocumentId getDocumentId(DataLocation) const override {
        return document::DocumentId("");
    }

    void readBody(const document::DocumentTypeRepo&,
                  DataLocation,
                  Document&) const override
    {
    }
    DataLocation addDocumentIdOnlyHeader(
            const DocumentId&,
            const document::DocumentTypeRepo&) override
    {
        return DataLocation();
    }
    DataLocation addHeader(const Document&) override { return DataLocation(); }
    DataLocation addBody(const Document&) override { return DataLocation(); }
    void clear(DocumentPart) override {}
    bool verifyConsistent() const override { return true; }
    void move(const FileSpecification&) override {}
    DataLocation copyCache(const MemFileIOInterface&, DocumentPart, DataLocation) override {
        return DataLocation();
    }

    void close() override {};
    bool isCached(DataLocation, DocumentPart) const override { return false; }
    bool isPersisted(DataLocation, DocumentPart) const override { return false; }
    uint32_t getSerializedSize(DocumentPart, DataLocation) const override { return 0; }

    void ensureCached(Environment&, DocumentPart, const std::vector<DataLocation>&) override {}

    size_t getCachedSize(DocumentPart) const override { return 0; }
};

#define VESPA_MEMFILEV1_SETUP_SOURCE \
    system("rm -f testfile.0"); \
    document::Document::SP doc(createRandomDocumentAtLocation(4)); \
    FileSpecification file(document::BucketId(16, 4), env().getDirectory(0), "testfile.0");                                  \
    MemFile source(file, env());

#define VESPA_MEMFILEV1_DIFF(source, target) \
    "\nSource:\n" + source.toString(true) \
    + "\nTarget:\n" + target.toString(true)

#define VESPA_MEMFILEV1_VALIDATE_STRUCTURE(mfile) \
{ \
    std::ostringstream validateErr; \
    if (!validateMemFileStructure(mfile, validateErr)) { \
        CPPUNIT_FAIL(validateErr.str()); \
    } \
}

#define VESPA_MEMFILEV1_ASSERT_SERIALIZATION(sourceMemFile) \
env()._memFileMapper.flush(sourceMemFile, env()); \
VESPA_MEMFILEV1_VALIDATE_STRUCTURE(sourceMemFile) \
MemFile target(file, env()); \
VESPA_MEMFILEV1_VALIDATE_STRUCTURE(target) \
{ \
    target.ensureBodyBlockCached(); \
    target.getBucketInfo(); \
    std::ostringstream diff; \
    if (!isContentEqual(sourceMemFile, target, true, diff)) { \
        std::string msg = "MemFiles not content equal: " + diff.str() \
                        + VESPA_MEMFILEV1_DIFF(sourceMemFile, target); \
        CPPUNIT_FAIL(msg); \
    } \
}

void
MemFileV1SerializerTest::testWriteReadSingleDoc()
{
    VESPA_MEMFILEV1_SETUP_SOURCE;
    source.addPutSlot(*doc, Timestamp(1001));
    std::string foo(VESPA_MEMFILEV1_DIFF(source, source));
    VESPA_MEMFILEV1_ASSERT_SERIALIZATION(source);
}

void
MemFileV1SerializerTest::testWriteReadPartial()
{
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    std::map<Timestamp, Document::SP> docs;
    {
        MemFile source(file, env());

        for (int i = 0; i < 50; ++i) {
            Document::SP doc(createRandomDocumentAtLocation(4, i, 1000, 2000));
            source.addPutSlot(*doc, Timestamp(1001 + i));
            docs[Timestamp(1001 + i)] = doc;
        }

        env()._memFileMapper.flush(source, env());
        VESPA_MEMFILEV1_VALIDATE_STRUCTURE(source);
    }

    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options).maximumReadThroughGap(1024).build());
    env()._lazyFileFactory = std::unique_ptr<Environment::LazyFileFactory>(
            new LoggingLazyFile::Factory());

    MemFile target(file, env());

    std::vector<Timestamp> timestamps;

    for (int i = 0; i < 50; i+=4) {
        timestamps.push_back(Timestamp(1001 + i));
    }
    CPPUNIT_ASSERT_EQUAL(size_t(13), timestamps.size());

    getLoggerFile(target).operations.clear();
    target.ensureDocumentCached(timestamps, false);
    // Headers are small enough that they get read in 1 op + 13 body reads
    CPPUNIT_ASSERT_EQUAL(14, (int)getLoggerFile(target).operations.size());

    for (std::size_t i = 0; i < timestamps.size(); ++i) {
        const MemSlot* slot = target.getSlotAtTime(timestamps[i]);
        CPPUNIT_ASSERT(slot);
        CPPUNIT_ASSERT(target.partAvailable(*slot, HEADER));
        CPPUNIT_ASSERT(target.partAvailable(*slot, BODY));
        CPPUNIT_ASSERT_EQUAL(*docs[timestamps[i]], *target.getDocument(*slot, ALL));
    }
    VESPA_MEMFILEV1_VALIDATE_STRUCTURE(target);
}

void
MemFileV1SerializerTest::testWriteReadPartialRemoved()
{
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    MemFile source(file, env());

    for (int i = 0; i < 50; ++i) {
        Document::SP doc(createRandomDocumentAtLocation(4, i, 1000, 2000));
        source.addPutSlot(*doc, Timestamp(1001 + i));
        source.addRemoveSlot(*source.getSlotAtTime(Timestamp(1001 + i)),
                             Timestamp(2001 + i));
    }

    env()._memFileMapper.flush(source, env());
    VESPA_MEMFILEV1_VALIDATE_STRUCTURE(source);
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options).maximumReadThroughGap(1024).build());
    env()._lazyFileFactory = std::unique_ptr<Environment::LazyFileFactory>(
            new LoggingLazyFile::Factory);

    MemFile target(file, env());

    std::vector<Timestamp> timestamps;

    for (int i = 0; i < 50; i+=4) {
        timestamps.push_back(Timestamp(2001 + i));
    }

    getLoggerFile(target).operations.clear();
    target.ensureDocumentCached(timestamps, false);
    // All removed; should only read header locations
    CPPUNIT_ASSERT_EQUAL(1, (int)getLoggerFile(target).operations.size());

    for (std::size_t i = 0; i < timestamps.size(); ++i) {
        const MemSlot* slot = target.getSlotAtTime(timestamps[i]);
        const MemSlot* removedPut(
                target.getSlotAtTime(timestamps[i] - Timestamp(1000)));
        CPPUNIT_ASSERT(slot);
        CPPUNIT_ASSERT(removedPut);
        CPPUNIT_ASSERT(target.partAvailable(*slot, HEADER));
        CPPUNIT_ASSERT_EQUAL(removedPut->getLocation(HEADER),
                             slot->getLocation(HEADER));
        CPPUNIT_ASSERT_EQUAL(DataLocation(0, 0), slot->getLocation(BODY));
    }
    VESPA_MEMFILEV1_VALIDATE_STRUCTURE(target);
}

void MemFileV1SerializerTest::testWriteReadSingleRemovedDoc()
{
    VESPA_MEMFILEV1_SETUP_SOURCE;
    source.addPutSlot(*doc, Timestamp(1001));
    source.addRemoveSlot(
            *source.getSlotAtTime(Timestamp(1001)), Timestamp(2001));
    VESPA_MEMFILEV1_ASSERT_SERIALIZATION(source);
}

/**
 * Write a single put with no body to the memfile and ensure it is
 * persisted properly without a body block
 */
void
MemFileV1SerializerTest::testPartialWritePutHeaderOnly()
{
    setUpPartialWriteEnvironment();
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    document::Document::SP doc(createRandomDocumentAtLocation(4));
    {
        MemFile source(file, env());
        source.addPutSlot(*doc, Timestamp(1001));
        env()._memFileMapper.flush(source, env());
        VESPA_MEMFILEV1_VALIDATE_STRUCTURE(source);
    }
    {
        // Have to put a second time since the first one will always
        // rewrite the entire file
        MemFile target(file, env());
        Document::SP doc2(createRandomDocumentAtLocation(4));
        clearBody(*doc2);
        target.addPutSlot(*doc2, Timestamp(1003));
        env()._memFileMapper.flush(target, env());
        VESPA_MEMFILEV1_VALIDATE_STRUCTURE(target);
    }
    {
        MemFile target(file, env());
        target.ensureBodyBlockCached();
        CPPUNIT_ASSERT_EQUAL(uint32_t(2), target.getSlotCount());

        const MemSlot& slot = *target.getSlotAtTime(Timestamp(1003));
        CPPUNIT_ASSERT(slot.getLocation(HEADER)._pos > 0);
        CPPUNIT_ASSERT(slot.getLocation(HEADER)._size > 0);
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(0, 0), slot.getLocation(BODY));
        VESPA_MEMFILEV1_VALIDATE_STRUCTURE(target);
    }
}




void
MemFileV1SerializerTest::testLocationDiskIoPlannerSimple()
{
    std::vector<MemSlot> slots;

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1001),
                        DataLocation(0, 1024),
                        DataLocation(4096, 512), 0, 0));
    }

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1003),
                        DataLocation(1024, 1024),
                        DataLocation(8192, 512), 0, 0));
    }

    std::vector<DataLocation> headers;
    std::vector<DataLocation> bodies;
    headers.push_back(slots[0].getLocation(HEADER));
    bodies.push_back(slots[0].getLocation(BODY));

    DummyMemFileIOInterface dummyIo;
    {
        LocationDiskIoPlanner planner(dummyIo, HEADER, headers, 100, 0);

        CPPUNIT_ASSERT_EQUAL(1, (int)planner.getIoOperations().size());
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(0, 1024),
                planner.getIoOperations()[0]);
    }
    {
        LocationDiskIoPlanner planner(dummyIo, BODY, bodies, 100, 4096);

        CPPUNIT_ASSERT_EQUAL(1, (int)planner.getIoOperations().size());
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(8192, 512), // + block index
                planner.getIoOperations()[0]);
    }
}

void
MemFileV1SerializerTest::testLocationDiskIoPlannerMergeReads()
{
    std::vector<MemSlot> slots;

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1001),
                        DataLocation(0, 1024),
                        DataLocation(5120, 512), 0, 0));
    }

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1002),
                        DataLocation(2048, 1024),
                        DataLocation(7168, 512), 0, 0));
    }

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1003),
                        DataLocation(1024, 1024),
                        DataLocation(9216, 512), 0, 0));
    }

    std::vector<DataLocation> headers;
    std::vector<DataLocation> bodies;
    for (int i = 0; i < 2; ++i) {
        headers.push_back(slots[i].getLocation(HEADER));
        bodies.push_back(slots[i].getLocation(BODY));
    }

    DummyMemFileIOInterface dummyIo;
    {
        LocationDiskIoPlanner planner(dummyIo, HEADER, headers, 1025, 0);

        CPPUNIT_ASSERT_EQUAL(1, (int)planner.getIoOperations().size());
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(0, 3072),
                planner.getIoOperations()[0]);
    }

    {
        LocationDiskIoPlanner planner(dummyIo, BODY, bodies, 1025, 0);

        CPPUNIT_ASSERT_EQUAL(2, (int)planner.getIoOperations().size());
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(5120, 512),
                planner.getIoOperations()[0]);
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(7168, 512),
                planner.getIoOperations()[1]);
    }
}

void
MemFileV1SerializerTest::testLocationDiskIoPlannerOneDocument()
{
    std::vector<MemSlot> slots;

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1001),
                        DataLocation(0, 1024),
                        DataLocation(5120, 512), 0, 0));
    }

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1002),
                        DataLocation(2048, 1024),
                        DataLocation(7168, 512), 0, 0));
    }

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1003),
                        DataLocation(1024, 1024),
                        DataLocation(9216, 512), 0, 0));
    }

    std::vector<DataLocation> headers;
    std::vector<DataLocation> bodies;
    headers.push_back(slots[1].getLocation(HEADER));
    bodies.push_back(slots[1].getLocation(BODY));

    DummyMemFileIOInterface dummyIo;
    {
        LocationDiskIoPlanner planner(dummyIo, HEADER, headers, 1000, 0);
        CPPUNIT_ASSERT_EQUAL(1, (int)planner.getIoOperations().size());
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(2048, 1024),
                planner.getIoOperations()[0]);
    }

    {
        LocationDiskIoPlanner planner(dummyIo, BODY, bodies, 1000, 0);
        CPPUNIT_ASSERT_EQUAL(1, (int)planner.getIoOperations().size());
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(7168, 512),
                planner.getIoOperations()[0]);
    }
}

void
MemFileV1SerializerTest::testLocationDiskIoPlannerAlignReads()
{
    std::vector<MemSlot> slots;

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1001),
                        DataLocation(7, 100),
                        DataLocation(5000, 500), 0, 0));
    }

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1002),
                        DataLocation(2000, 100),
                        DataLocation(7000, 500), 0, 0));
    }

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1003),
                        DataLocation(110, 200),
                        DataLocation(9000, 500), 0, 0));
    }

    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        slots.push_back(
                MemSlot(
                        doc->getId().getGlobalId(),
                        Timestamp(1004),
                        DataLocation(3000, 100),
                        DataLocation(11000, 500), 0, 0));
    }

    std::vector<DataLocation> headers;
    std::vector<DataLocation> bodies;
    for (int i = 0; i < 2; ++i) {
        headers.push_back(slots[i].getLocation(HEADER));
        bodies.push_back(slots[i].getLocation(BODY));
    }

    DummyMemFileIOInterface dummyIo;
    {
        LocationDiskIoPlanner planner(dummyIo, HEADER, headers, 512, 0);
        std::vector<DataLocation> expected;
        expected.push_back(DataLocation(0, 512));
        expected.push_back(DataLocation(1536, 1024));

        CPPUNIT_ASSERT_EQUAL(expected, planner.getIoOperations());
    }
    {
        LocationDiskIoPlanner planner(dummyIo, BODY, bodies, 512, 0);
        std::vector<DataLocation> expected;
        expected.push_back(DataLocation(4608, 1024));
        expected.push_back(DataLocation(6656, 1024));

        CPPUNIT_ASSERT_EQUAL(expected, planner.getIoOperations());
    }
}

// TODO(vekterli): add read planner test with a location cached

void
MemFileV1SerializerTest::testSeparateReadsForHeaderAndBody()
{
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    Document::SP doc(createRandomDocumentAtLocation(4, 0, 1000, 2000));
    {
        MemFile source(file, env());
        source.addPutSlot(*doc, Timestamp(1001));

        env()._memFileMapper.flush(source, env());
    }
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options)
                .maximumReadThroughGap(1024*1024*100)
                .build());
    env()._lazyFileFactory = std::unique_ptr<Environment::LazyFileFactory>(
            new LoggingLazyFile::Factory());

    MemFile target(file, env());

    std::vector<Timestamp> timestamps;
    timestamps.push_back(Timestamp(1001));

    getLoggerFile(target).operations.clear();
    target.ensureDocumentCached(timestamps, false);

    CPPUNIT_ASSERT_EQUAL(2, (int)getLoggerFile(target).operations.size());
    const MemSlot* slot = target.getSlotAtTime(Timestamp(1001));
    CPPUNIT_ASSERT(slot);
    CPPUNIT_ASSERT(target.partAvailable(*slot, HEADER));
    CPPUNIT_ASSERT(target.partAvailable(*slot, BODY));
    CPPUNIT_ASSERT_EQUAL(*doc, *target.getDocument(*slot, ALL));

    CPPUNIT_ASSERT(getMetrics().serialization.headerReadSize.getLast() > 0);
    CPPUNIT_ASSERT(getMetrics().serialization.bodyReadSize.getLast() > 0);
}

/**
 * Write a single put with body to the memfile and ensure it is
 * persisted properly with both header and body blocks
 */
void
MemFileV1SerializerTest::testPartialWritePut()
{
    setUpPartialWriteEnvironment();
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    Document::SP doc(createRandomDocumentAtLocation(4));
    {
        MemFile source(file, env());
        source.addPutSlot(*doc, Timestamp(1001));

        env()._memFileMapper.flush(source, env());
    }

    {
        // Have to put a second time since the first one will always
        // rewrite the entire file
        MemFile target(file, env());
        Document::SP doc2(createRandomDocumentAtLocation(4));
        target.addPutSlot(*doc2, Timestamp(1003));
        env()._memFileMapper.flush(target, env());
    }
    {
        MemFile target(file, env());
        target.ensureBodyBlockCached();
        CPPUNIT_ASSERT_EQUAL(uint32_t(2), target.getSlotCount());

        const MemSlot& slot = *target.getSlotAtTime(Timestamp(1003));
        CPPUNIT_ASSERT(slot.getLocation(HEADER)._pos > 0);
        CPPUNIT_ASSERT(slot.getLocation(HEADER)._size > 0);

        CPPUNIT_ASSERT(slot.getLocation(BODY)._size > 0);
        CPPUNIT_ASSERT(slot.getLocation(BODY)._pos > 0);
    }
}

void
MemFileV1SerializerTest::doTestPartialWriteRemove(bool readAll)
{
    setUpPartialWriteEnvironment();
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    Document::SP doc(createRandomDocumentAtLocation(4));
    {
        MemFile source(file, env());
        source.addPutSlot(*doc, Timestamp(1001));
        env()._memFileMapper.flush(source, env());
    }
    {
        MemFile target(file, env());
        // Only populate cache before removing if explicitly told so
        if (readAll) {
            target.ensureBodyBlockCached();
        }
        CPPUNIT_ASSERT_EQUAL(uint32_t(1), target.getSlotCount());
        target.addRemoveSlot(target[0], Timestamp(1003));

        env()._memFileMapper.flush(target, env());
    }
    {
        MemFile target(file, env());
        target.ensureBodyBlockCached();

        CPPUNIT_ASSERT_EQUAL(uint32_t(2), target.getSlotCount());

        const MemSlot& originalSlot = target[0];
        const MemSlot& removeSlot = target[1];
        CPPUNIT_ASSERT(originalSlot.getLocation(HEADER)._size > 0);
        CPPUNIT_ASSERT(originalSlot.getLocation(BODY)._size > 0);
        CPPUNIT_ASSERT_EQUAL(
                originalSlot.getLocation(HEADER),
                removeSlot.getLocation(HEADER));
        CPPUNIT_ASSERT_EQUAL(
                DataLocation(0, 0), removeSlot.getLocation(BODY));
    }
}

/**
 * Ensure that removes get the same header location as the Put
 * they're removing, and that they get a zero body location
 */
void
MemFileV1SerializerTest::testPartialWriteRemoveCached()
{
    doTestPartialWriteRemove(true);
}

void
MemFileV1SerializerTest::testPartialWriteRemoveNotCached()
{
    doTestPartialWriteRemove(false);
}

void
MemFileV1SerializerTest::doTestPartialWriteUpdate(bool readAll)
{
    setUpPartialWriteEnvironment();
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    Document::SP doc(createRandomDocumentAtLocation(4));
    {
        MemFile source(file, env());
        source.addPutSlot(*doc, Timestamp(1001));
        env()._memFileMapper.flush(source, env());
    }

    Document::SP doc2;
    {
        MemFile target(file, env());
        if (readAll) {
            target.ensureBodyBlockCached();
        }

        doc2.reset(new Document(*doc->getDataType(), doc->getId()));
        clearBody(*doc2);
        doc2->setValue(doc->getField("hstringval"),
                       document::StringFieldValue("Some updated content"));

        target.addUpdateSlot(*doc2, *target.getSlotAtTime(Timestamp(1001)),
                             Timestamp(1003));
        env()._memFileMapper.flush(target, env());
    }

    {
        MemFile target(file, env());
        CPPUNIT_ASSERT_EQUAL(uint32_t(2), target.getSlotCount());
        const MemSlot& originalSlot = target[0];
        const MemSlot& updateSlot = target[1];
        CPPUNIT_ASSERT(originalSlot.getLocation(HEADER)._size > 0);
        CPPUNIT_ASSERT(originalSlot.getLocation(BODY)._size > 0);
        CPPUNIT_ASSERT_EQUAL(
                originalSlot.getLocation(BODY),
                updateSlot.getLocation(BODY));
        CPPUNIT_ASSERT(
                updateSlot.getLocation(HEADER)
                != originalSlot.getLocation(HEADER));

        CPPUNIT_ASSERT_EQUAL(*doc, *target.getDocument(target[0], ALL));
        copyHeader(*doc, *doc2);
        CPPUNIT_ASSERT_EQUAL(*doc, *target.getDocument(target[1], ALL));
    }
}

/**
 * Ensure that header updates keep the same body block
 */
void
MemFileV1SerializerTest::testPartialWriteUpdateCached()
{
    doTestPartialWriteUpdate(true);
}

void
MemFileV1SerializerTest::testPartialWriteUpdateNotCached()
{
    doTestPartialWriteUpdate(false);
}

void
MemFileV1SerializerTest::testPartialWriteTooMuchFreeSpace()
{
    setUpPartialWriteEnvironment();
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    {
        MemFile source(file, env());
        Document::SP doc(createRandomDocumentAtLocation(4));
        source.addPutSlot(*doc, Timestamp(1001));
        env()._memFileMapper.flush(source, env());
    }
    int64_t sizeBefore;
    // Append filler to slotfile to make it too big for comfort,
    // forcing a rewrite to shrink it down
    {
        vespalib::File slotfile(file.getPath());
        slotfile.open(0);
        CPPUNIT_ASSERT(slotfile.isOpen());
        sizeBefore = slotfile.getFileSize();
        slotfile.resize(sizeBefore * 20); // Well over min fill rate of 10%
    }
    // Write new slot to file; it should now be rewritten with the
    // same file size as originally
    {
        MemFile source(file, env());
        Document::SP doc(createRandomDocumentAtLocation(4));
        source.addPutSlot(*doc, Timestamp(1003));
        env()._memFileMapper.flush(source, env());
    }
    {
        vespalib::File slotfile(file.getPath());
        slotfile.open(0);
        CPPUNIT_ASSERT(slotfile.isOpen());
        CPPUNIT_ASSERT_EQUAL(
                sizeBefore,
                slotfile.getFileSize());
    }
    CPPUNIT_ASSERT_EQUAL(uint64_t(1), getMetrics().serialization
                         .fullRewritesDueToDownsizingFile.getValue());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), getMetrics().serialization
                         .fullRewritesDueToTooSmallFile.getValue());
}

void
MemFileV1SerializerTest::testPartialWriteNotEnoughFreeSpace()
{
    setUpPartialWriteEnvironment();
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    // Write file initially
    MemFile source(file, env());
    {
        Document::SP doc(createRandomDocumentAtLocation(4));
        source.addPutSlot(*doc, Timestamp(1001));
        env()._memFileMapper.flush(source, env());
    }

    uint32_t minFile = 1024 * 512;
    auto memFileCfg = env().acquireConfigReadLock().memFilePersistenceConfig();
    resetConfig(minFile, memFileCfg->minimumFileHeaderBlockSize);

    // Create doc bigger than initial minimum filesize,
    // prompting a full rewrite
    Document::SP doc(
            createRandomDocumentAtLocation(4, 0, 4096, 4096));
    source.addPutSlot(*doc, Timestamp(1003));

    env()._memFileMapper.flush(source, env());

    CPPUNIT_ASSERT_EQUAL(
            minFile,
            uint32_t(getFileHandle(source).getFileSize()));

    CPPUNIT_ASSERT_EQUAL(uint64_t(0), getMetrics().serialization
                         .fullRewritesDueToDownsizingFile.getValue());
    CPPUNIT_ASSERT_EQUAL(uint64_t(1), getMetrics().serialization
                         .fullRewritesDueToTooSmallFile.getValue());

    // Now, ensure we respect minimum file size and don't try to
    // "helpfully" rewrite the file again (try to detect full
    // file rewrite with help from the fact we don't currently
    // check whether or not the file is < the minimum filesize.
    // If that changes, so must this)
    memFileCfg = env().acquireConfigReadLock().memFilePersistenceConfig();
    resetConfig(2 * minFile, memFileCfg->minimumFileHeaderBlockSize);

    source.addRemoveSlot(*source.getSlotAtTime(Timestamp(1003)),
                         Timestamp(1005));
    env()._memFileMapper.flush(source, env());

    CPPUNIT_ASSERT_EQUAL(
            minFile,
            uint32_t(getFileHandle(source).getFileSize()));

    CPPUNIT_ASSERT_EQUAL(uint64_t(1), getMetrics().serialization
                         .fullRewritesDueToTooSmallFile.getValue());
}

// Test that we don't mess up when remapping locations that
// have already been written during the same operation. That is:
//   part A is remapped (P1, S1) -> (P2, S2)
//   part B is remapped (P2, S2) -> (P3, S3)
// Obviously, part B should not overwrite the location of part A,
// but this will happen if we don't do the updating in one batch.
void
MemFileV1SerializerTest::testLocationsRemappedConsistently()
{
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");

    std::map<Timestamp, Document::SP> docs;
    {
        MemFile mf(file, env());
        Document::SP tmpDoc(
                createRandomDocumentAtLocation(4, 0, 100, 100));

        // Create docs identical in size but differing only in doc ids
        // By keeping same size but inserting with _lower_ timestamps
        // for docs that get higher location positions, we ensure that
        // when the file is rewritten, the lower timestamp slots will
        // get remapped to locations that match existing locations for
        // higher timestamp slots.
        for (int i = 0; i < 2; ++i) {
            std::ostringstream ss;
            ss << "doc" << i;
            DocumentId id(document::UserDocIdString("userdoc:foo:4:" + ss.str()));
            Document::SP doc(new Document(*tmpDoc->getDataType(), id));
            doc->getFields() = tmpDoc->getFields();
            mf.addPutSlot(*doc, Timestamp(1000 - i));
            docs[Timestamp(1000 - i)] = doc;
        }

        env()._memFileMapper.flush(mf, env());
        // Dirty the cache for rewrite
        {
            DocumentId id2(document::UserDocIdString("userdoc:foo:4:doc9"));
            Document::UP doc2(new Document(*tmpDoc->getDataType(), id2));
            doc2->getFields() = tmpDoc->getFields();
            mf.addPutSlot(*doc2, Timestamp(2000));
            docs[Timestamp(2000)] = std::move(doc2);
        }

        // Force rewrite
        auto memFileCfg = env().acquireConfigReadLock()
                            .memFilePersistenceConfig();
        resetConfig(1024*512, memFileCfg ->minimumFileHeaderBlockSize);
        env()._memFileMapper.flush(mf, env());
    }

    MemFile target(file, env());
    target.ensureBodyBlockCached();

    std::ostringstream err;
    if (!env()._memFileMapper.verify(target, env(), err)) {
        std::cerr << err.str() << "\n";
        CPPUNIT_FAIL("MemFile verification failed");
    }

    typedef std::map<Timestamp, Document::SP>::iterator Iter;
    for (Iter it(docs.begin()); it != docs.end(); ++it) {
        const MemSlot* slot = target.getSlotAtTime(it->first);
        CPPUNIT_ASSERT(slot);
        CPPUNIT_ASSERT(target.partAvailable(*slot, HEADER));
        CPPUNIT_ASSERT(target.partAvailable(*slot, BODY));
        CPPUNIT_ASSERT_EQUAL(*it->second, *target.getDocument(*slot, ALL));
    }
}

/**
 * Test that we read in the correct header information when we have to read
 * in two passes to get it in its entirety.
 */
void
MemFileV1SerializerTest::testHeaderBufferTooSmall()
{
    system("rm -f testfile.0");
    FileSpecification file(BucketId(16, 4), env().getDirectory(0), "testfile.0");
    FileInfo wantedInfo;
    {
        MemFile f(file, env());
        // 50*40 bytes of meta list data should be more than sufficient
        for (size_t i = 0; i < 50; ++i) {
            Document::SP doc(createRandomDocumentAtLocation(4, i));
            f.addPutSlot(*doc, Timestamp(1001 + i));
            env()._memFileMapper.flush(f, env());
        }
        SimpleMemFileIOBuffer& io(
                dynamic_cast<SimpleMemFileIOBuffer&>(f.getMemFileIO()));
        wantedInfo = io.getFileInfo();
    }

    // Force initial index read to be too small to contain all metadata,
    // triggering buffer resize and secondary read.
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options).initialIndexRead(512).build());
    {
        MemFile f(file, env());
        CPPUNIT_ASSERT_EQUAL(uint32_t(50), f.getSlotCount());
        // Ensure we've read correct file info
        SimpleMemFileIOBuffer& io(
                dynamic_cast<SimpleMemFileIOBuffer&>(f.getMemFileIO()));
        const FileInfo& info(io.getFileInfo());
        CPPUNIT_ASSERT_EQUAL(wantedInfo.getFileSize(), info.getFileSize());
        CPPUNIT_ASSERT_EQUAL(wantedInfo.getHeaderBlockStartIndex(),
                             info.getHeaderBlockStartIndex());
        CPPUNIT_ASSERT_EQUAL(wantedInfo.getBodyBlockStartIndex(),
                             info.getBodyBlockStartIndex());
        CPPUNIT_ASSERT_EQUAL(wantedInfo.getBlockSize(HEADER),
                             info.getBlockSize(HEADER));
        CPPUNIT_ASSERT_EQUAL(wantedInfo.getBlockSize(BODY),
                             info.getBlockSize(BODY));
    }
}

} // memfile
} // storage
