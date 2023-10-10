// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/mmap_file_allocator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <sys/mman.h>

using vespalib::alloc::MemoryAllocator;
using vespalib::alloc::MmapFileAllocator;
using vespalib::alloc::PtrAndSize;

namespace {

vespalib::string basedir("mmap-file-allocator-dir");
vespalib::string hello("hello");
vespalib::string world("world");

struct MyAlloc
{
    const MemoryAllocator& allocator;
    void*                  data;
    size_t                 size;

    MyAlloc(MemoryAllocator& allocator_in, PtrAndSize buf)
        : allocator(allocator_in),
          data(buf.get()),
          size(buf.size())
    {
    }

    ~MyAlloc()
    {
        allocator.free(data, size);
    }

    PtrAndSize asPair() const noexcept { return PtrAndSize(data, size); }
};

}

struct AllocatorSetup {
    uint32_t small_limit;
    uint32_t premmap_size;

    AllocatorSetup(uint32_t small_limit_in, uint32_t premmap_size_in)
        : small_limit(small_limit_in),
          premmap_size(premmap_size_in)
    {
    }
};

std::ostream& operator<<(std::ostream& os, const AllocatorSetup setup)
{
    os << "small" << setup.small_limit << "premm" << setup.premmap_size;
    return os;
}

class MmapFileAllocatorTest : public ::testing::TestWithParam<AllocatorSetup>
{
protected:
    MmapFileAllocator _allocator;

public:
    MmapFileAllocatorTest();
    ~MmapFileAllocatorTest();
};

MmapFileAllocatorTest::MmapFileAllocatorTest()
    : _allocator(basedir, GetParam().small_limit, GetParam().premmap_size)
{
}

MmapFileAllocatorTest::~MmapFileAllocatorTest() = default;

INSTANTIATE_TEST_SUITE_P(MmapFileAllocatorMultiTest,
                         MmapFileAllocatorTest,
                         testing::Values(AllocatorSetup(0, 1_Mi), AllocatorSetup(512, 1_Mi), AllocatorSetup(128_Ki, 1_Mi)), testing::PrintToStringParamName());



TEST_P(MmapFileAllocatorTest, zero_sized_allocation_is_handled)
{
    MyAlloc buf(_allocator, _allocator.alloc(0));
    EXPECT_EQ(nullptr, buf.data);
    EXPECT_EQ(0u, buf.size);
}

TEST_P(MmapFileAllocatorTest, mmap_file_allocator_works)
{
    MyAlloc buf(_allocator, _allocator.alloc(300));
    EXPECT_LE(300u, buf.size);
    EXPECT_TRUE(buf.data != nullptr);
    memcpy(buf.data, "1st", 4);
    MyAlloc buf2(_allocator, _allocator.alloc(600));
    EXPECT_LE(600u, buf2.size);
    EXPECT_TRUE(buf2.data != nullptr);
    EXPECT_TRUE(buf.data != buf2.data);
    memcpy(buf2.data, "fine", 5);
    EXPECT_EQ(0u, _allocator.resize_inplace(buf.asPair(), 500));
    EXPECT_EQ(0u, _allocator.resize_inplace(buf.asPair(), 200));
    EXPECT_NE(0u, _allocator.get_end_offset());
    if (GetParam().small_limit == 0) {
        int result = msync(buf.data, buf.size, MS_SYNC);
        EXPECT_EQ(0, result);
        result = msync(buf2.data, buf2.size, MS_SYNC);
        EXPECT_EQ(0, result);
    }
}

TEST_P(MmapFileAllocatorTest, reuse_file_offset_works)
{
    constexpr size_t size_300 = 300;
    constexpr size_t size_600 = 600;
    assert(hello.size() + 1 <= size_300);
    assert(world.size() + 1 <= size_600);
    {
        MyAlloc buf(_allocator, _allocator.alloc(size_300));
        memcpy(buf.data, hello.c_str(), hello.size() + 1);
    }
    {
        MyAlloc buf(_allocator, _allocator.alloc(size_300));
        EXPECT_EQ(0, memcmp(buf.data, hello.c_str(), hello.size() + 1));
    }
    {
        MyAlloc buf(_allocator, _allocator.alloc(size_600));
        memcpy(buf.data, world.c_str(), world.size() + 1);
    }
    {
        MyAlloc buf(_allocator, _allocator.alloc(size_600));
        EXPECT_EQ(0, memcmp(buf.data, world.c_str(), world.size() + 1));
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
