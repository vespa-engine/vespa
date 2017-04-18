// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/memfilepersistence/mapper/memfile_v1_serializer.h>
#include <vespa/memfilepersistence/mapper/memfile_v1_verifier.h>
#include <vespa/memfilepersistence/mapper/fileinfo.h>
#include <vespa/memfilepersistence/mapper/simplememfileiobuffer.h>
#include <tests/spi/memfiletestutils.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace memfile {

class MemFileAutoRepairTest : public SingleDiskMemFileTestUtils
{
public:
    void setUp() override;
    void tearDown() override;

    void testFileMetadataCorruptionIsAutoRepaired();
    void testDocumentContentCorruptionIsAutoRepaired();
    void testCorruptionEvictsBucketFromCache();
    void testRepairFailureInMaintainEvictsBucketFromCache();
    void testZeroLengthFileIsDeleted();
    void testTruncatedBodyLocationIsAutoRepaired();
    void testTruncatedHeaderLocationIsAutoRepaired();
    void testTruncatedHeaderBlockIsAutoRepaired();

    void corruptBodyBlock();

    CPPUNIT_TEST_SUITE(MemFileAutoRepairTest);
    CPPUNIT_TEST(testFileMetadataCorruptionIsAutoRepaired);
    CPPUNIT_TEST(testDocumentContentCorruptionIsAutoRepaired);
    CPPUNIT_TEST(testCorruptionEvictsBucketFromCache);
    CPPUNIT_TEST(testRepairFailureInMaintainEvictsBucketFromCache);
    CPPUNIT_TEST(testZeroLengthFileIsDeleted);
    CPPUNIT_TEST(testTruncatedBodyLocationIsAutoRepaired);
    CPPUNIT_TEST(testTruncatedHeaderLocationIsAutoRepaired);
    CPPUNIT_TEST(testTruncatedHeaderBlockIsAutoRepaired);
    CPPUNIT_TEST_SUITE_END();

private:
    void assertDocumentIsSilentlyRemoved(
            const document::BucketId& bucket,
            const document::DocumentId& docId);

    void reconfigureMinimumHeaderBlockSize(uint32_t newMinSize);

    document::BucketId _bucket;
    std::unique_ptr<FileSpecification> _file;
    std::vector<document::DocumentId> _slotIds;
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemFileAutoRepairTest);

namespace {
    // A totall uncached memfile with content to use for verify testing
    std::unique_ptr<MemFile> _memFile;

    // Clear old content. Create new file. Make sure nothing is cached.
    void prepareBucket(SingleDiskMemFileTestUtils& util,
                       const FileSpecification& file) {
        _memFile.reset();
        util.env()._cache.clear();
        vespalib::unlink(file.getPath());
        util.createTestBucket(file.getBucketId(), 0);
        util.env()._cache.clear();
        _memFile.reset(new MemFile(file, util.env()));
        _memFile->getMemFileIO().close();

    }

    MetaSlot getSlot(uint32_t index) {
        assert(_memFile.get());
        vespalib::LazyFile file(_memFile->getFile().getPath(), 0);
        MetaSlot result;
        file.read(&result, sizeof(MetaSlot),
                  sizeof(Header) + sizeof(MetaSlot) * index);
        return result;
    }

    void setSlot(uint32_t index, MetaSlot slot,
                 bool updateFileChecksum = true)
    {
        (void)updateFileChecksum;
        assert(_memFile.get());
        //if (updateFileChecksum) slot.updateFileChecksum();
        vespalib::LazyFile file(_memFile->getFile().getPath(), 0);
        file.write(&slot, sizeof(MetaSlot),
                   sizeof(Header) + sizeof(MetaSlot) * index);
    }
}

void
MemFileAutoRepairTest::setUp()
{
    SingleDiskMemFileTestUtils::setUp();
    _bucket = BucketId(16, 0xa);
    createTestBucket(_bucket, 0);

    {
        MemFilePtr memFilePtr(env()._cache.get(_bucket, env(), env().getDirectory()));
        _file.reset(new FileSpecification(memFilePtr->getFile()));
        CPPUNIT_ASSERT(memFilePtr->getSlotCount() >= 2);
        for (size_t i = 0; i < memFilePtr->getSlotCount(); ++i) {
            _slotIds.push_back(memFilePtr->getDocumentId((*memFilePtr)[i]));
        }
    }
    env()._cache.clear();
}

void
MemFileAutoRepairTest::tearDown()
{
    _file.reset(0);
    _memFile.reset(0);
    SingleDiskMemFileTestUtils::tearDown();
};

void
MemFileAutoRepairTest::testFileMetadataCorruptionIsAutoRepaired()
{
    // Test corruption detected in initial metadata load
    prepareBucket(*this, *_file);
    document::DocumentId id(_slotIds[1]);
    MetaSlot slot(getSlot(1));
    CPPUNIT_ASSERT(slot._gid == id.getGlobalId()); // Sanity checking...
    {
        MetaSlot s(slot);
        s.setTimestamp(Timestamp(40));
        setSlot(1, s);
    }

    CPPUNIT_ASSERT_EQUAL(std::string(""), getModifiedBuckets());
    
    // File not in cache; should be detected in initial load
    spi::GetResult res(doGet(_bucket, id, document::AllFields()));
    // FIXME: currently loadFile is silently fixing corruptions!
    //CPPUNIT_ASSERT_EQUAL(spi::Result::TRANSIENT_ERROR, res.getErrorCode());
    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, res.getErrorCode());
    CPPUNIT_ASSERT(!res.hasDocument());

    CPPUNIT_ASSERT_EQUAL(std::string("400000000000000a"), getModifiedBuckets());
    CPPUNIT_ASSERT_EQUAL(std::string(""), getModifiedBuckets());

    // File should now have been repaired, so a subsequent get for
    // the same document should just return an empty (but OK) result.
    spi::GetResult res2(doGet(_bucket, id, document::AllFields()));
    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, res2.getErrorCode());
    CPPUNIT_ASSERT(!res2.hasDocument());

    CPPUNIT_ASSERT_EQUAL(std::string(""), getModifiedBuckets());
}

void
MemFileAutoRepairTest::corruptBodyBlock()
{
    CPPUNIT_ASSERT(!env()._cache.contains(_bucket));
    // Corrupt body block of slot 1
    MetaSlot slot(getSlot(1));
    {
        MetaSlot s(slot);
        s.setBodyPos(52);
        s.setBodySize(18);
        s.updateChecksum();
        setSlot(1, s);
    }
}

void
MemFileAutoRepairTest::testDocumentContentCorruptionIsAutoRepaired()
{
    // Corrupt body block
    prepareBucket(*this, *_file);
    document::DocumentId id(_slotIds[1]);
    corruptBodyBlock();

    CPPUNIT_ASSERT_EQUAL(std::string(""), getModifiedBuckets());
    
    spi::GetResult res(doGet(_bucket, id, document::AllFields()));
    CPPUNIT_ASSERT_EQUAL(spi::Result::TRANSIENT_ERROR, res.getErrorCode());
    CPPUNIT_ASSERT(!res.hasDocument());

    CPPUNIT_ASSERT(!env()._cache.contains(_bucket));

    CPPUNIT_ASSERT_EQUAL(std::string("400000000000000a"), getModifiedBuckets());
    CPPUNIT_ASSERT_EQUAL(std::string(""), getModifiedBuckets());

    // File should now have been repaired, so a subsequent get for
    // the same document should just return an empty (but OK) result.
    spi::GetResult res2(doGet(_bucket, id, document::AllFields()));
    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, res2.getErrorCode());
    CPPUNIT_ASSERT(!res2.hasDocument());

    // File should now be in cache OK
    CPPUNIT_ASSERT(env()._cache.contains(_bucket));
    CPPUNIT_ASSERT_EQUAL(std::string(""), getModifiedBuckets());
}

// Ideally we'd test this for each spi operation that accesses MemFiles, but
// they all use the same eviction+auto-repair logic...
void
MemFileAutoRepairTest::testCorruptionEvictsBucketFromCache()
{
    prepareBucket(*this, *_file);
    corruptBodyBlock();

    // Read slot 0 and shove file into cache
    spi::GetResult res(doGet(_bucket, _slotIds[0], document::AllFields()));
    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, res.getErrorCode());
    CPPUNIT_ASSERT(res.hasDocument());
    CPPUNIT_ASSERT(env()._cache.contains(_bucket));

    spi::GetResult res2(doGet(_bucket, _slotIds[1], document::AllFields()));
    CPPUNIT_ASSERT_EQUAL(spi::Result::TRANSIENT_ERROR, res2.getErrorCode());
    CPPUNIT_ASSERT(!res2.hasDocument());

    // Out of the cache! Begone! Shoo!
    CPPUNIT_ASSERT(!env()._cache.contains(_bucket));

}

void
MemFileAutoRepairTest::testRepairFailureInMaintainEvictsBucketFromCache()
{
    prepareBucket(*this, *_file);
    corruptBodyBlock();
    spi::Result result(getPersistenceProvider().maintain(
            spi::Bucket(_bucket, spi::PartitionId(0)), spi::HIGH));
    // File being successfully repaired does not constitute a failure of
    // the maintain() call.
    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, result.getErrorCode());
    // It should, however, shove it out of the cache.
    CPPUNIT_ASSERT(!env()._cache.contains(_bucket));
}

void
MemFileAutoRepairTest::testZeroLengthFileIsDeleted()
{
    // Completely truncate auto-created file
    vespalib::LazyFile file(_file->getPath(), 0);
    file.resize(0);

    // No way to deal with zero-length files aside from deleting them.
    spi::Result result(getPersistenceProvider().maintain(
            spi::Bucket(_bucket, spi::PartitionId(0)), spi::HIGH));
    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, result.getErrorCode());
    CPPUNIT_ASSERT(!env()._cache.contains(_bucket));
    CPPUNIT_ASSERT(!vespalib::fileExists(_file->getPath()));
}

namespace {

uint32_t
alignDown(uint32_t value)
{
    uint32_t blocks = value / 512;
    return blocks * 512;
};

FileInfo
fileInfoFromMemFile(const MemFilePtr& mf)
{
    auto& ioBuf(dynamic_cast<const SimpleMemFileIOBuffer&>(
                mf->getMemFileIO()));
    return ioBuf.getFileInfo();
}

}

void
MemFileAutoRepairTest::assertDocumentIsSilentlyRemoved(
        const document::BucketId& bucket,
        const document::DocumentId& docId)
{
    // Corrupted (truncated) slot should be transparently removed during
    // loadFile and it should be as if it was never there!
    spi::Bucket spiBucket(bucket, spi::PartitionId(0));
    spi::GetResult res(doGet(spiBucket, docId, document::AllFields()));
    CPPUNIT_ASSERT_EQUAL(spi::Result::NONE, res.getErrorCode());
    CPPUNIT_ASSERT(!res.hasDocument());
}

void
MemFileAutoRepairTest::testTruncatedBodyLocationIsAutoRepaired()
{
    document::BucketId bucket(16, 4);
    document::Document::SP doc(
            createRandomDocumentAtLocation(4, 1234, 1024, 1024));

    doPut(doc, bucket, framework::MicroSecTime(1000));
    flush(bucket);
    FileInfo fileInfo;
    {
        MemFilePtr mf(getMemFile(bucket));
        CPPUNIT_ASSERT_EQUAL(uint32_t(1), mf->getSlotCount());
        fileInfo = fileInfoFromMemFile(mf);

        const uint32_t bodyBlockStart(
                sizeof(Header)
                + fileInfo._metaDataListSize * sizeof(MetaSlot)
                + fileInfo._headerBlockSize);

        vespalib::LazyFile file(mf->getFile().getPath(), 0);
        uint32_t slotBodySize = (*mf)[0].getLocation(BODY)._size;
        CPPUNIT_ASSERT(slotBodySize > 0);
        // Align down to nearest sector alignment to avoid unrelated DirectIO
        // checks to kick in. Since the body block is always aligned on a
        // sector boundary, we know this cannot truncate into the header block.
        file.resize(alignDown(bodyBlockStart + slotBodySize - 1));
    }
    env()._cache.clear();
    assertDocumentIsSilentlyRemoved(bucket, doc->getId());
}

void
MemFileAutoRepairTest::testTruncatedHeaderLocationIsAutoRepaired()
{
    document::BucketId bucket(16, 4);
    document::Document::SP doc(
            createRandomDocumentAtLocation(4, 1234, 1024, 1024));
    // Ensure header has a bunch of data (see alignment comments below).
    doc->setValue(doc->getField("hstringval"),
                  document::StringFieldValue(std::string(1024, 'A')));

    doPut(doc, bucket, framework::MicroSecTime(1000));
    flush(bucket);
    FileInfo fileInfo;
    {
        MemFilePtr mf(getMemFile(bucket));
        CPPUNIT_ASSERT_EQUAL(uint32_t(1), mf->getSlotCount());
        fileInfo = fileInfoFromMemFile(mf);

        const uint32_t headerBlockStart(
                sizeof(Header)
                + fileInfo._metaDataListSize * sizeof(MetaSlot));

        vespalib::LazyFile file(mf->getFile().getPath(), 0);
        uint32_t slotHeaderSize = (*mf)[0].getLocation(HEADER)._size;
        CPPUNIT_ASSERT(slotHeaderSize > 0);
        // Align down to nearest sector alignment to avoid unrelated DirectIO
        // checks to kick in. The header block is not guaranteed to start on
        // sector boundary, but we assume there is enough slack in the header
        // section for the metadata slots themselves to be untouched since we
        // have a minimum header size of 1024 for the doc in question.
        file.resize(alignDown(headerBlockStart + slotHeaderSize - 1));
    }
    env()._cache.clear();
    assertDocumentIsSilentlyRemoved(bucket, doc->getId());
}

void
MemFileAutoRepairTest::reconfigureMinimumHeaderBlockSize(uint32_t newMinSize)
{
    using MemFileConfig = vespa::config::storage::StorMemfilepersistenceConfig;
    using MemFileConfigBuilder
        = vespa::config::storage::StorMemfilepersistenceConfigBuilder;
    MemFileConfigBuilder builder(
            *env().acquireConfigReadLock().memFilePersistenceConfig());
    builder.minimumFileMetaSlots = 2;
    builder.minimumFileHeaderBlockSize = newMinSize;
    auto newConfig = std::unique_ptr<MemFileConfig>(new MemFileConfig(builder));
    env().acquireConfigWriteLock().setMemFilePersistenceConfig(
            std::move(newConfig));
}

void
MemFileAutoRepairTest::testTruncatedHeaderBlockIsAutoRepaired()
{
    document::BucketId bucket(16, 4);
    document::Document::SP doc(
            createRandomDocumentAtLocation(4, 1234, 1, 1));
    // Ensure header block is large enough that free space is added to the end.
    reconfigureMinimumHeaderBlockSize(8192);
    // Add header field and remove randomly generated body field, ensuring
    // we have no data to add to body field. This will prevent slot body
    // location checking from detecting a header truncation.
    doc->setValue(doc->getField("hstringval"),
                  document::StringFieldValue("foo"));
    doc->remove(doc->getField("content"));

    doPut(doc, bucket, framework::MicroSecTime(1000));
    flush(bucket);
    FileInfo fileInfo;
    {
        MemFilePtr mf(getMemFile(bucket));
        CPPUNIT_ASSERT_EQUAL(uint32_t(1), mf->getSlotCount());
        fileInfo = fileInfoFromMemFile(mf);

        const uint32_t headerBlockEnd(
                sizeof(Header)
                + fileInfo._metaDataListSize * sizeof(MetaSlot)
                + fileInfo._headerBlockSize);

        vespalib::LazyFile file(mf->getFile().getPath(), 0);
        CPPUNIT_ASSERT_EQUAL(uint32_t(0),
                             (*mf)[0].getLocation(BODY)._size); // No body.
        const auto headerLoc((*mf)[0].getLocation(HEADER));
        const uint32_t extent(headerLoc._pos + headerLoc._size);
        // Make sure we don't intersect an existing slot range.
        CPPUNIT_ASSERT(extent < alignDown(headerBlockEnd - 1));
        file.resize(alignDown(headerBlockEnd - 1));
    }
    env()._cache.clear();
    assertDocumentIsSilentlyRemoved(bucket, doc->getId());
}

}
}
