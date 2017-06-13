// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/memfilepersistence/mapper/buffer.h>

namespace storage {
namespace memfile {

class BufferTest : public CppUnit::TestFixture
{
public:
    void getSizeReturnsInitiallyAllocatedSize();
    void getSizeReturnsUnAlignedSizeForMMappedAllocs();
    void resizeRetainsExistingDataWhenSizingUp();
    void resizeRetainsExistingDataWhenSizingDown();
    void bufferAddressIs512ByteAligned();

    CPPUNIT_TEST_SUITE(BufferTest);
    CPPUNIT_TEST(getSizeReturnsInitiallyAllocatedSize);
    CPPUNIT_TEST(getSizeReturnsUnAlignedSizeForMMappedAllocs);
    CPPUNIT_TEST(resizeRetainsExistingDataWhenSizingUp);
    CPPUNIT_TEST(resizeRetainsExistingDataWhenSizingDown);
    CPPUNIT_TEST(bufferAddressIs512ByteAligned);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(BufferTest);

void
BufferTest::getSizeReturnsInitiallyAllocatedSize()
{
    Buffer buf(1234);
    CPPUNIT_ASSERT_EQUAL(size_t(1234), buf.getSize());
}

void
BufferTest::getSizeReturnsUnAlignedSizeForMMappedAllocs()
{
    Buffer buf(vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE + 1);
    CPPUNIT_ASSERT_EQUAL(size_t(vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE + 1), buf.getSize());
}

void
BufferTest::resizeRetainsExistingDataWhenSizingUp()
{
    std::string src = "hello world";
    Buffer buf(src.size());
    memcpy(buf.getBuffer(), src.data(), src.size());
    buf.resize(src.size() * 2);
    CPPUNIT_ASSERT_EQUAL(src.size() * 2, buf.getSize());
    CPPUNIT_ASSERT_EQUAL(0, memcmp(buf.getBuffer(), src.data(), src.size()));
}

void
BufferTest::resizeRetainsExistingDataWhenSizingDown()
{
    std::string src = "hello world";
    Buffer buf(src.size());
    memcpy(buf.getBuffer(), src.data(), src.size());
    buf.resize(src.size() / 2);
    CPPUNIT_ASSERT_EQUAL(src.size() / 2, buf.getSize());
    CPPUNIT_ASSERT_EQUAL(0, memcmp(buf.getBuffer(), src.data(), src.size() / 2));
}

void
BufferTest::bufferAddressIs512ByteAligned()
{
    Buffer buf(32);
    CPPUNIT_ASSERT(reinterpret_cast<size_t>(buf.getBuffer()) % 512 == 0);
}

} // memfile
} // storage

