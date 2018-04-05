// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/mapper/simplememfileiobuffer.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <tests/spi/memfiletestutils.h>
#include <tests/spi/options_builder.h>

namespace storage {
namespace memfile {

class SimpleMemFileIOBufferTest : public SingleDiskMemFileTestUtils
{
    CPPUNIT_TEST_SUITE(SimpleMemFileIOBufferTest);
    CPPUNIT_TEST(testAddAndReadDocument);
    CPPUNIT_TEST(testNonExistingLocation);
    CPPUNIT_TEST(testCopy);
    CPPUNIT_TEST(testCacheLocation);
    CPPUNIT_TEST(testPersist);
    CPPUNIT_TEST(testGetSerializedSize);
    CPPUNIT_TEST(testRemapLocations);
    CPPUNIT_TEST(testAlignmentUtilFunctions);
    CPPUNIT_TEST(testCalculatedCacheSize);
    CPPUNIT_TEST(testSharedBuffer);
    CPPUNIT_TEST(testSharedBufferUsage);
    CPPUNIT_TEST(testHeaderChunkEncoderComputesSizesCorrectly);
    CPPUNIT_TEST(testHeaderChunkEncoderSerializesIdCorrectly);
    CPPUNIT_TEST(testHeaderChunkEncoderSerializesHeaderCorrectly);
    CPPUNIT_TEST(testRemovesCanBeWrittenWithBlankDefaultDocument);
    CPPUNIT_TEST(testRemovesCanBeWrittenWithIdInferredDoctype);
    CPPUNIT_TEST(testRemovesWithInvalidDocTypeThrowsException);
    CPPUNIT_TEST_SUITE_END();

    using BufferType = SimpleMemFileIOBuffer::BufferType;
    using BufferSP = BufferType::SP;
    using BufferAllocation = SimpleMemFileIOBuffer::BufferAllocation;
    using HeaderChunkEncoder = SimpleMemFileIOBuffer::HeaderChunkEncoder;
    using SimpleMemFileIOBufferUP = std::unique_ptr<SimpleMemFileIOBuffer>;

    BufferAllocation allocateBuffer(size_t sz) {
        return BufferAllocation(BufferSP(new BufferType(sz)), 0, sz);
    }

    /**
     * Create an I/O buffer instance with for a dummy bucket. If removeDocType
     * is non-empty, remove entries will be written in backwards compatible
     * mode.
     */
    SimpleMemFileIOBufferUP createIoBufferWithDummySpec(
            vespalib::stringref removeDocType = "");

public:
    class DummyFileReader : public VersionSerializer {
    public:
        FileVersion getFileVersion() override { return FileVersion(); }
        void loadFile(MemFile&, Environment&, Buffer&, uint64_t ) override {}
        FlushResult flushUpdatesToFile(MemFile&, Environment&) override {
            return FlushResult::TooSmall;
        }
        void rewriteFile(MemFile&, Environment&) override {}
        bool verify(MemFile&, Environment&, std::ostream&, bool, uint16_t) override { return false; };
        void cacheLocations(MemFileIOInterface&, Environment&, const Options&,
                            DocumentPart, const std::vector<DataLocation>&) override {}
    };

    DummyFileReader dfr;

    void testAddAndReadDocument();
    void testNonExistingLocation();
    void testCopy();
    void testCacheLocation();
    void testPersist();
    void testGetSerializedSize();
    void testRemapLocations();
    void testAlignmentUtilFunctions();
    void testCalculatedCacheSize();
    void testSharedBuffer();
    void testSharedBufferUsage();
    void testHeaderChunkEncoderComputesSizesCorrectly();
    void testHeaderChunkEncoderSerializesIdCorrectly();
    void testHeaderChunkEncoderSerializesHeaderCorrectly();
    void testRemovesCanBeWrittenWithBlankDefaultDocument();
    void testRemovesCanBeWrittenWithIdInferredDoctype();
    void testRemovesWithInvalidDocTypeThrowsException();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SimpleMemFileIOBufferTest);


void
SimpleMemFileIOBufferTest::testAddAndReadDocument()
{
    FileSpecification fileSpec(BucketId(16, 123), env().getDirectory(), "testfile.0");
    document::Document::SP doc(createRandomDocumentAtLocation(
                                       123,
                                       456,
                                       789,
                                       1234));

    SimpleMemFileIOBuffer buffer(dfr,
                                 vespalib::LazyFile::UP(),
                                 std::unique_ptr<FileInfo>(new FileInfo),
                                 fileSpec,
                                 env());

    DataLocation h = buffer.addHeader(*doc);
    DataLocation b = buffer.addBody(*doc);

    Document::UP newDoc = buffer.getDocumentHeader(*getTypeRepo(), h);
    buffer.readBody(*getTypeRepo(), b, *newDoc);

    CPPUNIT_ASSERT_EQUAL(*doc, *newDoc);
    CPPUNIT_ASSERT_EQUAL(true, buffer.isCached(h, HEADER));
    CPPUNIT_ASSERT_EQUAL(true, buffer.isCached(b, BODY));
    CPPUNIT_ASSERT_EQUAL(false, buffer.isCached(h, BODY));
    CPPUNIT_ASSERT_EQUAL(false, buffer.isCached(b, HEADER));
    CPPUNIT_ASSERT_EQUAL(doc->getId(), buffer.getDocumentId(h));
}

void
SimpleMemFileIOBufferTest::testPersist()
{
    FileSpecification fileSpec(BucketId(16, 123), env().getDirectory(), "testfile.0");
    document::Document::SP doc(createRandomDocumentAtLocation(
                                       123,
                                       456,
                                       789,
                                       1234));

    SimpleMemFileIOBuffer buffer(dfr,
                                 vespalib::LazyFile::UP(),
                                 std::unique_ptr<FileInfo>(new FileInfo),
                                 fileSpec,
                                 env());

    DataLocation h = buffer.addHeader(*doc);
    DataLocation b = buffer.addBody(*doc);

    CPPUNIT_ASSERT(!buffer.isPersisted(h, HEADER));
    CPPUNIT_ASSERT(!buffer.isPersisted(b, BODY));

    buffer.persist(HEADER, h, DataLocation(1000, h.size()));
    buffer.persist(BODY, b, DataLocation(5000, b.size()));

    Document::UP newDoc = buffer.getDocumentHeader(*getTypeRepo(), DataLocation(1000, h.size()));
    buffer.readBody(*getTypeRepo(), DataLocation(5000, b.size()), *newDoc);

    CPPUNIT_ASSERT(buffer.isPersisted(DataLocation(1000, h.size()), HEADER));
    CPPUNIT_ASSERT(buffer.isPersisted(DataLocation(5000, b.size()), BODY));

    CPPUNIT_ASSERT_EQUAL(*doc, *newDoc);
}

void
SimpleMemFileIOBufferTest::testCopy()
{
    FileSpecification fileSpec(BucketId(16, 123), env().getDirectory(), "testfile.0");
    SimpleMemFileIOBuffer buffer(dfr,
                                 vespalib::LazyFile::UP(),
                                 std::unique_ptr<FileInfo>(new FileInfo),
                                 fileSpec,
                                 env());

    for (uint32_t i = 0; i < 10; ++i) {
        document::Document::SP doc(createRandomDocumentAtLocation(
                                           123,
                                           456,
                                           789,
                                           1234));

        DataLocation h = buffer.addHeader(*doc);
        DataLocation b = buffer.addBody(*doc);

        SimpleMemFileIOBuffer buffer2(dfr,
                                      vespalib::LazyFile::UP(),
                                      std::unique_ptr<FileInfo>(new FileInfo),
                                      fileSpec,
                                      env());

        DataLocation h2 = buffer2.copyCache(buffer, HEADER, h);
        DataLocation b2 = buffer2.copyCache(buffer, BODY, b);

        Document::UP newDoc = buffer2.getDocumentHeader(*getTypeRepo(), h2);
        buffer2.readBody(*getTypeRepo(), b2, *newDoc);

        CPPUNIT_ASSERT_EQUAL(*doc, *newDoc);
    }
}

void
SimpleMemFileIOBufferTest::testNonExistingLocation()
{
    FileSpecification fileSpec(BucketId(16, 123), env().getDirectory(), "testfile.0");
    document::Document::SP doc(createRandomDocumentAtLocation(
                                       123,
                                       456,
                                       789,
                                       1234));

    SimpleMemFileIOBuffer buffer(dfr,
                                 vespalib::LazyFile::UP(),
                                 std::unique_ptr<FileInfo>(new FileInfo),
                                 fileSpec,
                                 env());

    DataLocation h = buffer.addHeader(*doc);
    DataLocation b = buffer.addBody(*doc);

    buffer.clear(HEADER);

    try {
        Document::UP newDoc = buffer.getDocumentHeader(*getTypeRepo(), h);
        CPPUNIT_ASSERT(false);
    } catch (SimpleMemFileIOBuffer::PartNotCachedException& e) {
    }

    buffer.clear(BODY);

    try {
        document::Document newDoc;
        buffer.readBody(*getTypeRepo(), b, newDoc);
        CPPUNIT_ASSERT(false);
    } catch (SimpleMemFileIOBuffer::PartNotCachedException& e) {
    }
}

void
SimpleMemFileIOBufferTest::testCacheLocation()
{
    FileSpecification fileSpec(BucketId(16, 123), env().getDirectory(), "testfile.0");

    SimpleMemFileIOBuffer buffer(dfr,
                                 vespalib::LazyFile::UP(),
                                 FileInfo::UP(new FileInfo(100, 10000, 50000)),
                                 fileSpec,
                                 env());

    document::Document::SP doc(createRandomDocumentAtLocation(
                                       123,
                                       456,
                                       789,
                                       1234));

    BufferAllocation headerBuf = buffer.serializeHeader(*doc);
    BufferAllocation bodyBuf = buffer.serializeBody(*doc);

    DataLocation hloc(1234, headerBuf.getSize());
    DataLocation bloc(5678, bodyBuf.getSize());

    buffer.cacheLocation(HEADER, hloc, headerBuf.getSharedBuffer(), 0);
    buffer.cacheLocation(BODY, bloc, bodyBuf.getSharedBuffer(), 0);

    Document::UP newDoc = buffer.getDocumentHeader(*getTypeRepo(), hloc);
    buffer.readBody(*getTypeRepo(), bloc, *newDoc);

    CPPUNIT_ASSERT_EQUAL(*doc, *newDoc);
}

void
SimpleMemFileIOBufferTest::testGetSerializedSize()
{
    FileSpecification fileSpec(BucketId(16, 123), env().getDirectory(), "testfile.0");

    SimpleMemFileIOBuffer buffer(dfr,
                                 vespalib::LazyFile::UP(),
                                 FileInfo::UP(new FileInfo(100, 10000, 50000)),
                                 fileSpec,
                                 env());

    document::Document::SP doc(createRandomDocumentAtLocation(
                                       123,
                                       456,
                                       789,
                                       1234));

    BufferAllocation headerBuf = buffer.serializeHeader(*doc);
    BufferAllocation bodyBuf = buffer.serializeBody(*doc);

    DataLocation hloc(1234, headerBuf.getSize());
    DataLocation bloc(5678, bodyBuf.getSize());

    buffer.cacheLocation(HEADER, hloc, headerBuf.getSharedBuffer(), 0);
    buffer.cacheLocation(BODY, bloc, bodyBuf.getSharedBuffer(), 0);

    vespalib::nbostream serializedHeader;
    doc->serializeHeader(serializedHeader);

    vespalib::nbostream serializedBody;
    doc->serializeBody(serializedBody);

    CPPUNIT_ASSERT_EQUAL(uint32_t(serializedHeader.size()),
                         buffer.getSerializedSize(HEADER, hloc));
    CPPUNIT_ASSERT_EQUAL(uint32_t(serializedBody.size()),
                         buffer.getSerializedSize(BODY, bloc));
}

// Test that remapping does not overwrite datalocations that it has
// already updated
void
SimpleMemFileIOBufferTest::testRemapLocations()
{
    FileSpecification fileSpec(BucketId(16, 123), env().getDirectory(), "testfile.0");

    SimpleMemFileIOBuffer buffer(dfr,
                                 vespalib::LazyFile::UP(),
                                 FileInfo::UP(new FileInfo(100, 10000, 50000)),
                                 fileSpec,
                                 env());

    document::Document::SP doc(createRandomDocumentAtLocation(
                                       123,
                                       100,
                                       100));
    BufferAllocation headerBuf = buffer.serializeHeader(*doc);
    BufferAllocation bodyBuf = buffer.serializeBody(*doc);

    document::Document::SP doc2(createRandomDocumentAtLocation(
                                       123,
                                       100,
                                       100));

    BufferAllocation headerBuf2 = buffer.serializeHeader(*doc2);
    BufferAllocation bodyBuf2 = buffer.serializeBody(*doc2);

    DataLocation hloc(30000, headerBuf.getSize());
    DataLocation hloc2(0, headerBuf2.getSize());
    DataLocation hloc3(10000, hloc2._size);

    buffer.cacheLocation(HEADER, hloc, headerBuf.getSharedBuffer(), 0);
    buffer.cacheLocation(HEADER, hloc2, headerBuf2.getSharedBuffer(), 0);

    std::map<DataLocation, DataLocation> remapping;
    remapping[hloc2] = hloc;
    remapping[hloc] = hloc3;

    buffer.remapAndPersistAllLocations(HEADER, remapping);

    Document::UP newDoc = buffer.getDocumentHeader(*getTypeRepo(), hloc3);
    document::ByteBuffer bbuf(bodyBuf.getBuffer(), bodyBuf.getSize());
    newDoc->deserializeBody(*getTypeRepo(), bbuf);

    CPPUNIT_ASSERT_EQUAL(*doc, *newDoc);

    Document::UP newDoc2 = buffer.getDocumentHeader(*getTypeRepo(), hloc);
    document::ByteBuffer bbuf2(bodyBuf.getBuffer(), bodyBuf.getSize());
    newDoc2->deserializeBody(*getTypeRepo(), bbuf2);
    CPPUNIT_ASSERT_EQUAL(*doc2, *newDoc2);
}

/**
 * Not technically a part of SimpleMemFileIOBuffer, but used by it and
 * currently contained within its header file. Move test somewhere else
 * if the code itself is moved.
 */
void
SimpleMemFileIOBufferTest::testAlignmentUtilFunctions()
{
    using namespace util;
    CPPUNIT_ASSERT_EQUAL(size_t(0), alignUpPow2<4096>(0));
    CPPUNIT_ASSERT_EQUAL(size_t(4096), alignUpPow2<4096>(1));
    CPPUNIT_ASSERT_EQUAL(size_t(4096), alignUpPow2<4096>(512));
    CPPUNIT_ASSERT_EQUAL(size_t(4096), alignUpPow2<4096>(4096));
    CPPUNIT_ASSERT_EQUAL(size_t(8192), alignUpPow2<4096>(4097));
    CPPUNIT_ASSERT_EQUAL(size_t(32), alignUpPow2<16>(20));
    CPPUNIT_ASSERT_EQUAL(size_t(32), alignUpPow2<32>(20));
    CPPUNIT_ASSERT_EQUAL(size_t(64), alignUpPow2<64>(20));
    CPPUNIT_ASSERT_EQUAL(size_t(128), alignUpPow2<128>(20));

    CPPUNIT_ASSERT_EQUAL(uint32_t(0), nextPow2(0));
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), nextPow2(1));
    CPPUNIT_ASSERT_EQUAL(uint32_t(4), nextPow2(3));
    CPPUNIT_ASSERT_EQUAL(uint32_t(16), nextPow2(15));
    CPPUNIT_ASSERT_EQUAL(uint32_t(64), nextPow2(40));
    CPPUNIT_ASSERT_EQUAL(uint32_t(64), nextPow2(64));
}

/**
 * Test that allocated buffers are correctly reported with their sizes
 * rounded up to account for mmap overhead.
 */
void
SimpleMemFileIOBufferTest::testCalculatedCacheSize()
{
    FileSpecification fileSpec(BucketId(16, 123),
                               env().getDirectory(), "testfile.0");
    SimpleMemFileIOBuffer buffer(dfr,
                                 vespalib::LazyFile::UP(),
                                 std::unique_ptr<FileInfo>(new FileInfo),
                                 fileSpec,
                                 env());

    CPPUNIT_ASSERT_EQUAL(size_t(0), buffer.getCachedSize(HEADER));
    CPPUNIT_ASSERT_EQUAL(size_t(0), buffer.getCachedSize(BODY));

    // All buffers are on a 4k page granularity.
    BufferAllocation sharedHeaderBuffer(allocateBuffer(1500)); // -> 4096
    buffer.cacheLocation(HEADER, DataLocation(0, 85),
                         sharedHeaderBuffer.getSharedBuffer(), 0);
    CPPUNIT_ASSERT_EQUAL(size_t(4096), buffer.getCachedSize(HEADER));

    buffer.cacheLocation(HEADER, DataLocation(200, 100),
                         sharedHeaderBuffer.getSharedBuffer(), 85);
    CPPUNIT_ASSERT_EQUAL(size_t(4096), buffer.getCachedSize(HEADER));

    BufferAllocation singleHeaderBuffer(allocateBuffer(200)); // -> 4096
    buffer.cacheLocation(HEADER, DataLocation(0, 100),
                         singleHeaderBuffer.getSharedBuffer(), 0);
    CPPUNIT_ASSERT_EQUAL(size_t(8192), buffer.getCachedSize(HEADER));

    BufferAllocation singleBodyBuffer(allocateBuffer(300)); // -> 4096
    buffer.cacheLocation(BODY, DataLocation(0, 100),
                         singleBodyBuffer.getSharedBuffer(), 0);
    CPPUNIT_ASSERT_EQUAL(size_t(4096), buffer.getCachedSize(BODY));

    buffer.clear(HEADER);
    CPPUNIT_ASSERT_EQUAL(size_t(0), buffer.getCachedSize(HEADER));

    buffer.clear(BODY);
    CPPUNIT_ASSERT_EQUAL(size_t(0), buffer.getCachedSize(BODY));
}

void
SimpleMemFileIOBufferTest::testSharedBuffer()
{
    typedef SimpleMemFileIOBuffer::SharedBuffer SharedBuffer;

    {
        SharedBuffer buf(1024);
        CPPUNIT_ASSERT_EQUAL(size_t(1024), buf.getSize());
        CPPUNIT_ASSERT_EQUAL(size_t(1024), buf.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(size_t(0), buf.getUsedSize());
        CPPUNIT_ASSERT(buf.hasRoomFor(1024));
        CPPUNIT_ASSERT(!buf.hasRoomFor(1025));

        CPPUNIT_ASSERT_EQUAL(size_t(0), buf.allocate(13));
        // Allocation should be rounded up to nearest alignment.
        // TODO: is this even necessary?
        CPPUNIT_ASSERT_EQUAL(size_t(16), buf.getUsedSize());
        CPPUNIT_ASSERT_EQUAL(size_t(1008), buf.getFreeSize());
        CPPUNIT_ASSERT(buf.hasRoomFor(1008));
        CPPUNIT_ASSERT(!buf.hasRoomFor(1009));
        CPPUNIT_ASSERT_EQUAL(size_t(16), buf.allocate(1));
        CPPUNIT_ASSERT_EQUAL(size_t(24), buf.getUsedSize());

        CPPUNIT_ASSERT_EQUAL(size_t(24), buf.allocate(999));
        CPPUNIT_ASSERT(!buf.hasRoomFor(1));
        CPPUNIT_ASSERT_EQUAL(size_t(0), buf.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(size_t(1024), buf.getUsedSize());
    }
    // Test exact fit.
    {
        SharedBuffer buf(1024);
        CPPUNIT_ASSERT_EQUAL(size_t(0), buf.allocate(1024));
        CPPUNIT_ASSERT(!buf.hasRoomFor(1));
        CPPUNIT_ASSERT_EQUAL(size_t(0), buf.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(size_t(1024), buf.getUsedSize());
    }
    // Test 512-byte alignment.
    {
        SharedBuffer buf(1024);
        CPPUNIT_ASSERT(buf.hasRoomFor(1000, SharedBuffer::ALIGN_512_BYTES));
        CPPUNIT_ASSERT_EQUAL(size_t(0), buf.allocate(10));
        CPPUNIT_ASSERT(!buf.hasRoomFor(1000, SharedBuffer::ALIGN_512_BYTES));
        CPPUNIT_ASSERT(!buf.hasRoomFor(513, SharedBuffer::ALIGN_512_BYTES));
        CPPUNIT_ASSERT(buf.hasRoomFor(512, SharedBuffer::ALIGN_512_BYTES));
        CPPUNIT_ASSERT_EQUAL(size_t(512), buf.allocate(512, SharedBuffer::ALIGN_512_BYTES));
        CPPUNIT_ASSERT_EQUAL(size_t(0), buf.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(size_t(1024), buf.getUsedSize());
    }
}

void
SimpleMemFileIOBufferTest::testSharedBufferUsage()
{
    FileSpecification fileSpec(BucketId(16, 123),
                               env().getDirectory(), "testfile.0");
    SimpleMemFileIOBuffer ioBuf(dfr,
                                vespalib::LazyFile::UP(),
                                std::unique_ptr<FileInfo>(new FileInfo),
                                fileSpec,
                                env());

    const size_t threshold = SimpleMemFileIOBuffer::WORKING_BUFFER_SIZE;

    // Brand new allocation
    BufferAllocation ba(ioBuf.allocateBuffer(HEADER, 1));
    CPPUNIT_ASSERT(ba.buf.get());
    CPPUNIT_ASSERT_EQUAL(uint32_t(0), ba.pos);
    CPPUNIT_ASSERT_EQUAL(uint32_t(1), ba.size);
    // Should reuse buffer, but get other offset
    BufferAllocation ba2(ioBuf.allocateBuffer(HEADER, 500));
    CPPUNIT_ASSERT_EQUAL(ba.buf.get(), ba2.buf.get());
    CPPUNIT_ASSERT_EQUAL(uint32_t(8), ba2.pos);
    CPPUNIT_ASSERT_EQUAL(uint32_t(500), ba2.size);
    CPPUNIT_ASSERT_EQUAL(size_t(512), ba2.buf->getUsedSize());

    // Allocate a buffer so big that it should get its own buffer instance
    BufferAllocation ba3(ioBuf.allocateBuffer(HEADER, threshold));
    CPPUNIT_ASSERT(ba3.buf.get() != ba2.buf.get());
    CPPUNIT_ASSERT_EQUAL(uint32_t(0), ba3.pos);
    CPPUNIT_ASSERT_EQUAL(uint32_t(threshold), ba3.size);

    // But smaller allocs should still be done from working buffer
    BufferAllocation ba4(ioBuf.allocateBuffer(HEADER, 512));
    CPPUNIT_ASSERT_EQUAL(ba.buf.get(), ba4.buf.get());
    CPPUNIT_ASSERT_EQUAL(uint32_t(512), ba4.pos);
    CPPUNIT_ASSERT_EQUAL(uint32_t(512), ba4.size);
    CPPUNIT_ASSERT_EQUAL(size_t(1024), ba4.buf->getUsedSize());

    // Allocate lots of smaller buffers from the same buffer until we run out.
    while (true) {
        BufferAllocation tmp(ioBuf.allocateBuffer(HEADER, 1024));
        CPPUNIT_ASSERT_EQUAL(ba.buf.get(), tmp.buf.get());
        if (!tmp.buf->hasRoomFor(2048)) {
            break;
        }
    }
    BufferAllocation ba5(ioBuf.allocateBuffer(HEADER, 2048));
    CPPUNIT_ASSERT(ba5.buf.get() != ba.buf.get());
    CPPUNIT_ASSERT_EQUAL(uint32_t(0), ba5.pos);
    CPPUNIT_ASSERT_EQUAL(uint32_t(2048), ba5.size);

    // Allocating for different part should get different buffer.
    BufferAllocation ba6(ioBuf.allocateBuffer(BODY, 128));
    CPPUNIT_ASSERT(ba6.buf.get() != ba5.buf.get());
    CPPUNIT_ASSERT_EQUAL(uint32_t(0), ba6.pos);
    CPPUNIT_ASSERT_EQUAL(uint32_t(128), ba6.size);
}

void
SimpleMemFileIOBufferTest::testHeaderChunkEncoderComputesSizesCorrectly()
{
    document::Document::SP doc(createRandomDocumentAtLocation(123, 100, 100));

    std::string idString = doc->getId().toString();
    HeaderChunkEncoder encoder(doc->getId());
    // Without document, payload is: 3x u32 + doc id string (no zero term).
    CPPUNIT_ASSERT_EQUAL(sizeof(uint32_t)*3 + idString.size(),
                         static_cast<size_t>(encoder.encodedSize()));

    encoder.bufferDocument(*doc);
    vespalib::nbostream stream;
    doc->serializeHeader(stream);
    // With document, add size of serialized document to the mix.
    CPPUNIT_ASSERT_EQUAL(sizeof(uint32_t)*3 + idString.size() + stream.size(),
                         static_cast<size_t>(encoder.encodedSize()));
}

SimpleMemFileIOBufferTest::SimpleMemFileIOBufferUP
SimpleMemFileIOBufferTest::createIoBufferWithDummySpec(
        vespalib::stringref removeDocType)
{
    FileSpecification fileSpec(BucketId(16, 123),
                               env().getDirectory(), "testfile.0");
    // Override config.
    auto options = env().acquireConfigReadLock().options();
    env().acquireConfigWriteLock().setOptions(
            OptionsBuilder(*options)
                .defaultRemoveDocType(removeDocType)
                .build());

    SimpleMemFileIOBufferUP ioBuf(
            new SimpleMemFileIOBuffer(
                dfr,
                vespalib::LazyFile::UP(),
                std::unique_ptr<FileInfo>(new FileInfo),
                fileSpec,
                env()));
    return ioBuf;
}

void
SimpleMemFileIOBufferTest::testHeaderChunkEncoderSerializesIdCorrectly()
{
    document::Document::SP doc(createRandomDocumentAtLocation(123, 100, 100));
    HeaderChunkEncoder encoder(doc->getId());

    SimpleMemFileIOBufferUP ioBuf(createIoBufferWithDummySpec());

    BufferAllocation buf(ioBuf->allocateBuffer(HEADER, encoder.encodedSize()));
    encoder.writeTo(buf);
    DataLocation newLoc = ioBuf->addLocation(HEADER, buf);
    document::DocumentId checkId = ioBuf->getDocumentId(newLoc);

    CPPUNIT_ASSERT_EQUAL(doc->getId(), checkId);
}

void
SimpleMemFileIOBufferTest::testHeaderChunkEncoderSerializesHeaderCorrectly()
{
    document::Document::SP doc(createRandomDocumentAtLocation(123, 100, 100));
    HeaderChunkEncoder encoder(doc->getId());
    encoder.bufferDocument(*doc);

    SimpleMemFileIOBufferUP ioBuf(createIoBufferWithDummySpec());
    BufferAllocation buf(ioBuf->allocateBuffer(HEADER, encoder.encodedSize()));
    encoder.writeTo(buf);
    DataLocation newLoc = ioBuf->addLocation(HEADER, buf);
    Document::UP checkDoc = ioBuf->getDocumentHeader(*getTypeRepo(), newLoc);

    CPPUNIT_ASSERT_EQUAL(doc->getId(), checkDoc->getId());
    CPPUNIT_ASSERT_EQUAL(doc->getType(), checkDoc->getType());
}

void
SimpleMemFileIOBufferTest::testRemovesCanBeWrittenWithBlankDefaultDocument()
{
    SimpleMemFileIOBufferUP ioBuf(createIoBufferWithDummySpec("testdoctype1"));

    document::DocumentId id("userdoc:yarn:12345:fluff");
    DataLocation loc(ioBuf->addDocumentIdOnlyHeader(id, *getTypeRepo()));
    // Despite adding with document id only, we should now actually have a
    // valid document header. Will fail with a DeserializeException if no
    // header has been written.
    Document::UP removeWithHeader(
            ioBuf->getDocumentHeader(*getTypeRepo(), loc));
    CPPUNIT_ASSERT_EQUAL(removeWithHeader->getId(), id);
    CPPUNIT_ASSERT_EQUAL(removeWithHeader->getType(),
                         *getTypeRepo()->getDocumentType("testdoctype1"));
}

void
SimpleMemFileIOBufferTest::testRemovesCanBeWrittenWithIdInferredDoctype()
{
    SimpleMemFileIOBufferUP ioBuf(createIoBufferWithDummySpec("testdoctype1"));

    document::DocumentId id("id:yarn:testdoctype2:n=12345:fluff");
    DataLocation loc(ioBuf->addDocumentIdOnlyHeader(id, *getTypeRepo()));
    // Since document id contains an explicit document type, the blank remove
    // document header should be written with that type instead of the one
    // provided as default via config.
    Document::UP removeWithHeader(
            ioBuf->getDocumentHeader(*getTypeRepo(), loc));
    CPPUNIT_ASSERT_EQUAL(removeWithHeader->getId(), id);
    CPPUNIT_ASSERT_EQUAL(removeWithHeader->getType(),
                         *getTypeRepo()->getDocumentType("testdoctype2"));
}

void
SimpleMemFileIOBufferTest::testRemovesWithInvalidDocTypeThrowsException()
{
    SimpleMemFileIOBufferUP ioBuf(createIoBufferWithDummySpec("testdoctype1"));

    document::DocumentId id("id:yarn:nosuchtype:n=12345:fluff");
    try {
        DataLocation loc(ioBuf->addDocumentIdOnlyHeader(id, *getTypeRepo()));
        CPPUNIT_FAIL("No exception thrown on bad doctype");
    } catch (const vespalib::Exception& e) {
        CPPUNIT_ASSERT(e.getMessage().find("Could not serialize document "
                                           "for remove with unknown doctype "
                                           "'nosuchtype'")
                       != std::string::npos);
    }
}

} // memfile
} // storage
