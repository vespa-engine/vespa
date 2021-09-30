// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/mmap_file_allocator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <sys/mman.h>

using vespalib::alloc::MemoryAllocator;
using vespalib::alloc::MmapFileAllocator;

namespace {

vespalib::string basedir("mmap-file-allocator-dir");
vespalib::string hello("hello");

struct MyAlloc
{
    const MemoryAllocator& allocator;
    void*                  data;
    size_t                 size;

    MyAlloc(MemoryAllocator& allocator_in, MemoryAllocator::PtrAndSize buf)
        : allocator(allocator_in),
          data(buf.first),
          size(buf.second)
    {
    }

    ~MyAlloc()
    {
        allocator.free(data, size);
    }

    MemoryAllocator::PtrAndSize asPair() const noexcept { return std::make_pair(data, size); }
};

}

class MmapFileAllocatorTest : public ::testing::Test
{
protected:
    MmapFileAllocator _allocator;

public:
    MmapFileAllocatorTest();
    ~MmapFileAllocatorTest();
};

MmapFileAllocatorTest::MmapFileAllocatorTest()
    : _allocator(basedir)
{
}

MmapFileAllocatorTest::~MmapFileAllocatorTest() = default;

TEST_F(MmapFileAllocatorTest, zero_sized_allocation_is_handled)
{
    MyAlloc buf(_allocator, _allocator.alloc(0));
    EXPECT_EQ(nullptr, buf.data);
    EXPECT_EQ(0u, buf.size);
}

TEST_F(MmapFileAllocatorTest, mmap_file_allocator_works)
{
    MyAlloc buf(_allocator, _allocator.alloc(4));
    EXPECT_LE(4u, buf.size);
    EXPECT_TRUE(buf.data != nullptr);
    memcpy(buf.data, "1st", 4);
    MyAlloc buf2(_allocator, _allocator.alloc(5));
    EXPECT_LE(5u, buf2.size);
    EXPECT_TRUE(buf2.data != nullptr);
    EXPECT_TRUE(buf.data != buf2.data);
    memcpy(buf2.data, "fine", 5);
    EXPECT_EQ(0u, _allocator.resize_inplace(buf.asPair(), 5));
    EXPECT_EQ(0u, _allocator.resize_inplace(buf.asPair(), 3));
    EXPECT_NE(0u, _allocator.get_end_offset());
    int result = msync(buf.data, buf.size, MS_SYNC);
    EXPECT_EQ(0, result);
    result = msync(buf2.data, buf2.size, MS_SYNC);
    EXPECT_EQ(0, result);
}

TEST_F(MmapFileAllocatorTest, reuse_file_offset_works)
{
    {
        MyAlloc buf(_allocator, _allocator.alloc(hello.size() + 1));
        memcpy(buf.data, hello.c_str(), hello.size() + 1);
    }
    {
        MyAlloc buf(_allocator, _allocator.alloc(hello.size() + 1));
        EXPECT_EQ(0, memcmp(buf.data, hello.c_str(), hello.size() + 1));
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
