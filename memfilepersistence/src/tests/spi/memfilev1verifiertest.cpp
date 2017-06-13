// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/memfilepersistence/mapper/memfile_v1_serializer.h>
#include <vespa/memfilepersistence/mapper/memfile_v1_verifier.h>
#include <tests/spi/memfiletestutils.h>

namespace storage {
namespace memfile {

struct MemFileV1VerifierTest : public SingleDiskMemFileTestUtils
{
    void testVerify();

    void tearDown() override;

    std::unique_ptr<MemFile> createMemFile(FileSpecification& file,
                                         bool callLoadFile)
    {
        return std::unique_ptr<MemFile>(new MemFile(file, env(), callLoadFile));
    }

    CPPUNIT_TEST_SUITE(MemFileV1VerifierTest);
    CPPUNIT_TEST_IGNORED(testVerify);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemFileV1VerifierTest);

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

    // Get copy of header of memfile created
    Header getHeader() {
        assert(_memFile.get());
        vespalib::LazyFile file(_memFile->getFile().getPath(), 0);
        Header result;
        file.read(&result, sizeof(Header), 0);
        return result;
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

    void setHeader(const Header& header) {
        assert(_memFile.get());
        vespalib::LazyFile file(_memFile->getFile().getPath(), 0);
        file.write(&header, sizeof(Header), 0);
    }

    void verifySlotFile(MemFileV1VerifierTest& util,
                        const std::string& expectedError,
                        const std::string& message,
                        int32_t remainingEntries,
                        bool includeContent = true,
                        bool includeHeader = true)
    {
        assert(_memFile.get());
        FileSpecification file(_memFile->getFile());
        _memFile.reset();
        _memFile = util.createMemFile(file, false);
        std::ostringstream before;
        try{
            util.env()._memFileMapper.loadFile(*_memFile, util.env(), false);
            _memFile->print(before, true, "");
        } catch (vespalib::Exception& e) {
            before << "Unknown. Exception during loadFile\n";
        }
        std::ostringstream errors;
        uint32_t flags = (includeContent ? 0 : Types::DONT_VERIFY_BODY)
                       | (includeHeader ? 0 : Types::DONT_VERIFY_HEADER);
        if (util.env()._memFileMapper.verify(
                    *_memFile, util.env(), errors, flags))
        {
            _memFile->print(std::cerr, true, "");
            std::cerr << errors.str() << "\n";
            CPPUNIT_FAIL("verify() failed to detect: " + message);
        }
        CPPUNIT_ASSERT_CONTAIN_MESSAGE(message + "\nBefore: " + before.str(),
                                       expectedError, errors.str());
        errors.str("");
        if (util.env()._memFileMapper.repair(
                    *_memFile, util.env(), errors, flags))
        {
            CPPUNIT_FAIL("repair() failed to detect: " + message
                         + ": " + errors.str());
        }
        CPPUNIT_ASSERT_CONTAIN_MESSAGE(message + "\nBefore: " + before.str(),
                                       expectedError, errors.str());
        std::ostringstream remainingErrors;
        if (!util.env()._memFileMapper.verify(
                    *_memFile, util.env(), remainingErrors, flags))
        {
            CPPUNIT_FAIL("verify() returns issue after repair of: "
                         + message + ": " + remainingErrors.str());
        }
        CPPUNIT_ASSERT_MESSAGE(remainingErrors.str(),
                               remainingErrors.str().size() == 0);
        if (remainingEntries < 0) {
            if (_memFile->fileExists()) {
                CPPUNIT_FAIL(message + ": Expected file to not exist anymore");
            }
        } else if (dynamic_cast<SimpleMemFileIOBuffer&>(_memFile->getMemFileIO())
                   .getFileHandle().getFileSize() == 0)
        {
            std::ostringstream ost;
            ost << "Expected " << remainingEntries << " to remain in file, "
                << "but file does not exist\n";
            CPPUNIT_FAIL(message + ": " + ost.str());
        } else {
            if (int64_t(_memFile->getSlotCount()) != remainingEntries) {
                std::ostringstream ost;
                ost << "Expected " << remainingEntries << " to remain in file, "
                    << "but found " << _memFile->getSlotCount() << "\n";
                ost << errors.str() << "\n";
                ost << "Before: " << before.str() << "\nAfter: ";
                _memFile->print(ost, true, "");
                CPPUNIT_FAIL(message + ": " + ost.str());
            }
        }
    }
}

void
MemFileV1VerifierTest::tearDown()
{
    _memFile.reset(0);
    SingleDiskMemFileTestUtils::tearDown();
};

void
MemFileV1VerifierTest::testVerify()
{
    BucketId bucket(16, 0xa);
    std::unique_ptr<FileSpecification> file;
    createTestBucket(bucket, 0);

    {
        MemFilePtr memFilePtr(env()._cache.get(bucket, env(), env().getDirectory()));
        file.reset(new FileSpecification(memFilePtr->getFile()));
        env()._cache.clear();
    }
    {   // Ensure buildTestFile builds a valid file
            // Initial file should be fine.
        MemFile memFile(*file, env());
        std::ostringstream errors;
        if (!env()._memFileMapper.verify(memFile, env(), errors)) {
            memFile.print(std::cerr, false, "");
            CPPUNIT_FAIL("Slotfile failed verification: " + errors.str());
        }
    }
        // Header tests
    prepareBucket(*this, *file);
    Header orgheader(getHeader());
    {   // Test wrong version
        Header header(orgheader);
        header.setVersion(0xc0edbabe);
        header.updateChecksum();
        setHeader(header);
        verifySlotFile(*this,
                       "400000000000000a.0 is of wrong version",
                       "Faulty version",
                       -1);
    }
    {   // Test meta data list size bigger than file
        prepareBucket(*this, *file);
        Header header(orgheader);
        header.setMetaDataListSize(0xFFFF);
        header.updateChecksum();
        setHeader(header);
        verifySlotFile(*this,
                "indicates file is bigger than it physically is",
                "Too big meta data list size",
                -1);
    }
    {   // Test header block size bigger than file
        prepareBucket(*this, *file);
        Header header(orgheader);
        header.setHeaderBlockSize(0xFFFF);
        header.updateChecksum();
        setHeader(header);
        verifySlotFile(*this,
                "Header indicates file is bigger than it physically is",
                "Too big header block size",
                -1);
    }
    {   // Test wrong header crc
        prepareBucket(*this, *file);
        Header header(orgheader);
        header.setMetaDataListSize(4);
        setHeader(header);
        verifySlotFile(*this,
                "Header checksum mismatch",
                "Wrong header checksum",
                -1);
    }
        // Meta data tests
    prepareBucket(*this, *file);
    MetaSlot slot6(getSlot(6));
    {   // Test extra removes - currently allowed
        MetaSlot slot7(getSlot(7));
        MetaSlot s(slot7);
        s.setTimestamp(Timestamp(s._timestamp.getTime() - 1));
        s.updateChecksum();
        setSlot(6, s);
        s.setTimestamp(Timestamp(s._timestamp.getTime() + 1));
        s.updateChecksum();
        setSlot(7, s);
        std::ostringstream errors;
        if (!env()._memFileMapper.verify(*_memFile, env(), errors)) {
            _memFile->print(std::cerr, false, "");
            std::cerr << errors.str() << "\n";
            CPPUNIT_FAIL("Supposed to be legal with multiple remove values");
        }
        setSlot(7, slot7);
    }
    {
        // Test metadata crc mismatch with "used" flag being accidentally
        // flipped. Should not inhibit adding of subsequent slots.
        prepareBucket(*this, *file);
        MetaSlot s(slot6);
        s.setUseFlag(false);
        setSlot(6, s);
        verifySlotFile(*this,
                "Slot 6 at timestamp 2001 failed checksum verification",
                "Crc failure with use flag", 23, false);
    }
    {   // Test overlapping documents
        MetaSlot s(slot6);
            // Direct overlapping header
        prepareBucket(*this, *file);
        s.setHeaderPos(0);
        s.setHeaderSize(51);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "overlaps with slot",
                "Direct overlapping header", 6, false, false);
            // Contained header
            // (contained bit not valid header so fails on other error now)
        prepareBucket(*this, *file);
        s.setHeaderPos(176);
        s.setHeaderSize(80);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "not big enough to contain a document id",
                "Contained header", 7, false);
            // Partly overlapping header
            // (contained bit not valid header so fails on other error now)
        prepareBucket(*this, *file);
        s.setHeaderPos(191);
        s.setHeaderSize(35);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "not big enough to contain a document id",
                "Partly overlapping header", 7, false);
        prepareBucket(*this, *file);
        s.setHeaderPos(185);
        s.setHeaderSize(33);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "not big enough to contain a document id",
                "Partly overlapping header (2)", 7, false);
            // Direct overlapping body
        prepareBucket(*this, *file);
        s = slot6;
        s.setBodyPos(0);
        s.setBodySize(136);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "Multiple slots with different gids use same body position",
                "Directly overlapping body", 6, false);
            // Contained body
        prepareBucket(*this, *file);
        s.setBodyPos(10);
        s.setBodySize(50);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "overlaps with slot",
                "Contained body", 6, false);
        CPPUNIT_ASSERT(_memFile->getSlotAtTime(Timestamp(1)) == 0);
            // Overlapping body
        prepareBucket(*this, *file);
        s.setBodyPos(160);
        s.setBodySize(40);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "overlaps with slot",
                "Overlapping body", 5, false);
        CPPUNIT_ASSERT(_memFile->getSlotAtTime(Timestamp(2)) == 0);
        CPPUNIT_ASSERT(_memFile->getSlotAtTime(Timestamp(1501)) == 0);
            // Overlapping body, verifying bodies
            // (Bad body bit should be removed first, so only one slot needs
            // removing)
        prepareBucket(*this, *file);
        setSlot(6, s);
        verifySlotFile(*this,
                "Body checksum mismatch",
                "Overlapping body(2)", 7, true);
    }
    {   // Test out of bounds
        MetaSlot s(slot6);

            // Header out of bounds
        prepareBucket(*this, *file);
        s.setHeaderPos(500);
        s.setHeaderSize(5000);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "goes out of bounds",
                "Header out of bounds", 7, false, false);
            // Body out of bounds
        prepareBucket(*this, *file);
        s = slot6;
        s.setBodyPos(2400);
        s.setBodySize(6000);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "goes out of bounds",
                "Body out of bounds", 7, false);
    }
    {   // Test timestamp collision
        prepareBucket(*this, *file);
        MetaSlot s(slot6);
        s.setTimestamp(Timestamp(10002));
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "has same timestamp as slot 5",
                "Timestamp collision", 6, false);
    }
    {   // Test timestamp out of order
        prepareBucket(*this, *file);
        MetaSlot s(slot6);
        s.setTimestamp(Timestamp(38));
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "Slot 6 is out of timestamp order",
                "Timestamp out of order", 8, false);
    }
    {   // Test metadata crc mismatch
        prepareBucket(*this, *file);
        MetaSlot s(slot6);
        s.setTimestamp(Timestamp(40));
        setSlot(6, s);
        verifySlotFile(*this,
                "Slot 6 at timestamp 40 failed checksum verification",
                "Crc failure", 7, false);
    }
    {   // Test used after unused
        // This might actually lose documents after the unused entries.
        // The memfile will not know about the documents after unused entry.
        // If the memfile contains changes and writes metadata back due to this,
        // the following entries will be missing.
        // (To prevent this repair would have to add metadata entries, but that
        // may be problems if repair happens at a time where all header or body
        // data in the file needs to be cached.)
        prepareBucket(*this, *file);
        MetaSlot s(slot6);
        s.setUseFlag(false);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "Slot 7 found after unused entries",
                "Used after unused", 6, false);
    }
    {   // Test header blob corrupt
        prepareBucket(*this, *file);
        MetaSlot s(slot6);
        s.setHeaderPos(519);
        s.setHeaderSize(86);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "Header checksum mismatch",
                "Corrupt header blob.", 7);
    }
    {   // Test body blob corrupt
        prepareBucket(*this, *file);
        MetaSlot s(slot6);
        s.setBodyPos(52);
        s.setBodySize(18);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "Body checksum mismatch",
                "Corrupt body blob.", 7);
    }
    {   // Test too long name for header chunk
        prepareBucket(*this, *file);
        MetaSlot s(slot6);
        s.setHeaderPos(160);
        s.setHeaderSize(33);
        s.updateChecksum();
        setSlot(6, s);
        verifySlotFile(*this,
                "header is not big enough to contain a document",
                "Too long name in header.", 7);
    }
    {   // Test wrong file checksum
// Currently disabled. Currently only possible to calculate file checksum from
// memfile now, and memfile object wont be valid.
/*
            // First test if we actually have less entries at all..
        prepareBucket(*this, *file);
        MetaSlot s(getSlot(7));
        s.setUseFlag(false);
        s.updateChecksum();
        setSlot(7, s, false);
        s = getSlot(8);
        s.setUseFlag(false);
        s.updateChecksum();
        setSlot(8, s, false);
        verifySlotFile(*this,
                "File checksum should have been",
                "Wrong file checksum in file.", 7, false);
std::cerr << "U\n";
            // Then test with different timestamp in remaining document
        prepareBucket(*this, *file);
        s = getSlot(6);
        s.setTimestamp(s._timestamp + 1);
        s.updateChecksum();
        setSlot(6, s, false);
        verifySlotFile(*this,
                "File checksum should have been",
                "Wrong file checksum in file.", 9, false);
std::cerr << "V\n";
            // Then check with different gid
        prepareBucket(*this, *file);
        s = getSlot(6);
        s._gid = GlobalId("sdfsdfsedsdfsdfsd");
        s.updateChecksum();
        setSlot(6, s, false);
        verifySlotFile(*this,
                "File checksum should have been",
                "Wrong file checksum in file.", 9, false, false);
*/
    }
    {   // Test that documents not belonging in a bucket is removed
// Currently disabled. Hard to test. Needs total rewrite
/*
        prepareBucket(*this, *file);
        Blob b(createBlob(43u, "userdoc::0:315", "header", "body"));
        _memFile->write(b, 80);
        CPPUNIT_ASSERT_EQUAL(4u, _memFile->getBlobCount());
        CPPUNIT_ASSERT(_memFile->read(b));
        verifySlotFile(*this,
                "belongs in bucket",
                "Document not belonging there", 9);
        CPPUNIT_ASSERT_EQUAL(3u, _memFile->getBlobCount());
*/
    }
}

}
}
