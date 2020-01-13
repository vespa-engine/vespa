// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cstddef>

using namespace vespalib;
using namespace vespalib::alloc;

template <typename T>
void
testSwap(T & a, T & b)
{
    void * tmpA(a.get());
    void * tmpB(b.get());
    EXPECT_EQUAL(4096ul, a.size());
    EXPECT_EQUAL(8192ul, b.size());
    std::swap(a, b);
    EXPECT_EQUAL(4096ul, b.size());
    EXPECT_EQUAL(8192ul, a.size());
    EXPECT_EQUAL(tmpA, b.get());
    EXPECT_EQUAL(tmpB, a.get());
}

TEST("test roundUp2inN") {
    EXPECT_EQUAL(0u, roundUp2inN(0));
    EXPECT_EQUAL(2u, roundUp2inN(1));
    EXPECT_EQUAL(2u, roundUp2inN(2));
    EXPECT_EQUAL(4u, roundUp2inN(3));
    EXPECT_EQUAL(4u, roundUp2inN(4));
    EXPECT_EQUAL(8u, roundUp2inN(5));
    EXPECT_EQUAL(8u, roundUp2inN(6));
    EXPECT_EQUAL(8u, roundUp2inN(7));
    EXPECT_EQUAL(8u, roundUp2inN(8));
    EXPECT_EQUAL(16u, roundUp2inN(9));
}

TEST("test basics") {
    {
        Alloc h = Alloc::allocHeap(100);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        EXPECT_EXCEPTION(Alloc::allocAlignedHeap(100, 7), IllegalArgumentException, "Alloc::allocAlignedHeap(100, 7) does not support 7 alignment");
        Alloc h = Alloc::allocAlignedHeap(100, 1024);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        Alloc h = Alloc::allocMMap(100);
        EXPECT_EQUAL(4096u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        Alloc a = Alloc::allocHeap(4096), b = Alloc::allocHeap(8192);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocMMap(4096), b = Alloc::allocMMap(8192);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocAlignedHeap(4096, 1024), b = Alloc::allocAlignedHeap(8192, 1024);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocHeap(4096);
        Alloc b = Alloc::allocMMap(8192);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocHeap(100);
        Alloc b = Alloc::allocHeap(100);
        a = std::move(b);
        EXPECT_TRUE(b.get() == nullptr);
    }
}

TEST("test correct alignment") {
    {
        Alloc buf = Alloc::alloc(10, MemoryAllocator::HUGEPAGE_SIZE, 1024);
        EXPECT_TRUE(reinterpret_cast<ptrdiff_t>(buf.get()) % 1024 == 0);
    }

    {
        // Mmapped pointers are page-aligned, but sanity test anyway.
        Alloc buf = Alloc::alloc(3000000, MemoryAllocator::HUGEPAGE_SIZE, 512);
        EXPECT_TRUE(reinterpret_cast<ptrdiff_t>(buf.get()) % 512 == 0);
    }
}

TEST("no rounding of small heap buffer") {
    Alloc buf = Alloc::alloc(3, MemoryAllocator::HUGEPAGE_SIZE);
    EXPECT_EQUAL(3ul, buf.size());
}

TEST("no rounding of large heap buffer") {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE*11+3, MemoryAllocator::HUGEPAGE_SIZE*16);
    EXPECT_EQUAL(size_t(MemoryAllocator::HUGEPAGE_SIZE*11+3), buf.size());
}

TEST("rounding of small mmaped buffer") {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE);
    EXPECT_EQUAL(MemoryAllocator::HUGEPAGE_SIZE, buf.size());
    buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE+1);
    EXPECT_EQUAL(MemoryAllocator::HUGEPAGE_SIZE*2ul, buf.size());
}

TEST("rounding of large mmaped buffer") {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE*11+3);
    EXPECT_EQUAL(MemoryAllocator::HUGEPAGE_SIZE*12ul, buf.size());
}

TEST("heap alloc can not be extended") {
    Alloc buf = Alloc::allocHeap(100);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(100ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(101));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(100ul, buf.size());
}

TEST("auto alloced heap alloc can not be extended") {
    Alloc buf = Alloc::alloc(100);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(100ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(101));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(100ul, buf.size());
}

TEST("auto alloced heap alloc can not be extended, even if resize will be mmapped") {
    Alloc buf = Alloc::alloc(100);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(100ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(MemoryAllocator::HUGEPAGE_SIZE*3));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(100ul, buf.size());
}

void verifyExtension(Alloc & buf, size_t currSZ, size_t newSZ) {
    bool expectSuccess = (currSZ != newSZ);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(currSZ, buf.size());
    EXPECT_EQUAL(expectSuccess, buf.resize_inplace(currSZ+1));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(newSZ, buf.size());
}

void ensureRoomForExtension(const Alloc & buf, Alloc & reserved) {
    // Normally mmapping starts at the top and grows down in address space.
    // Then there is no room to extend the last mapping.
    // So in order to verify this we first mmap a reserved area that we unmap
    // before we test extension.
    if (reserved.get() > buf.get()) {
        EXPECT_EQUAL(reserved.get(), static_cast<const char *>(buf.get()) + buf.size());
        {
            Alloc().swap(reserved);
        }
    }
}

void verifyNoExtensionWhenNoRoom(Alloc & buf, Alloc & reserved, size_t sz) {
    if (reserved.get() > buf.get()) {
        // Normally mmapping starts at the top and grows down in address space.
        // Then there is no room to extend the last mapping.
        EXPECT_EQUAL(reserved.get(), static_cast<const char *>(buf.get()) + buf.size());
        TEST_DO(verifyExtension(buf, sz, sz));
    } else {
        EXPECT_EQUAL(buf.get(), static_cast<const char *>(reserved.get()) + reserved.size());
        TEST_DO(verifyExtension(reserved, sz, sz));
    }
}

TEST("auto alloced mmap alloc can be extended if room") {
    static constexpr size_t SZ = MemoryAllocator::HUGEPAGE_SIZE*2;
    Alloc reserved = Alloc::alloc(SZ);
    Alloc buf = Alloc::alloc(SZ);

    TEST_DO(ensureRoomForExtension(buf, reserved));
    TEST_DO(verifyExtension(buf, SZ, (SZ/2)*3));
}

TEST("auto alloced mmap alloc can not be extended if no room") {
    static constexpr size_t SZ = MemoryAllocator::HUGEPAGE_SIZE*2;
    Alloc reserved = Alloc::alloc(SZ);
    Alloc buf = Alloc::alloc(SZ);

    TEST_DO(verifyNoExtensionWhenNoRoom(buf, reserved, SZ));
}

TEST("mmap alloc can be extended if room") {
    Alloc dummy = Alloc::allocMMap(100);
    Alloc reserved = Alloc::allocMMap(100);
    Alloc buf = Alloc::allocMMap(100);

    TEST_DO(ensureRoomForExtension(buf, reserved));
    TEST_DO(verifyExtension(buf, 4096, 4096*2));
}

TEST("mmap alloc can not be extended if no room") {
    Alloc dummy = Alloc::allocMMap(100);
    Alloc reserved = Alloc::allocMMap(100);
    Alloc buf = Alloc::allocMMap(100);

    TEST_DO(verifyNoExtensionWhenNoRoom(buf, reserved, 4096));
}

TEST("heap alloc can not be shrinked") {
    Alloc buf = Alloc::allocHeap(101);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(101ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(100));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(101ul, buf.size());
}

TEST("mmap alloc can be shrinked") {
    Alloc buf = Alloc::allocMMap(4097);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(8192ul, buf.size());
    EXPECT_TRUE(buf.resize_inplace(4095));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(4096ul, buf.size());
}

TEST("auto alloced heap alloc can not be shrinked") {
    Alloc buf = Alloc::alloc(101);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(101ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(100));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(101ul, buf.size());
}

TEST("auto alloced mmap alloc can be shrinked") {
    static constexpr size_t SZ = MemoryAllocator::HUGEPAGE_SIZE;
    Alloc buf = Alloc::alloc(SZ + 1);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(SZ + MemoryAllocator::HUGEPAGE_SIZE, buf.size());
    EXPECT_TRUE(buf.resize_inplace(SZ-1));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(SZ, buf.size());
}

TEST("auto alloced mmap alloc can not be shrinked below HUGEPAGE_SIZE/2 + 1 ") {
    static constexpr size_t SZ = MemoryAllocator::HUGEPAGE_SIZE;
    Alloc buf = Alloc::alloc(SZ + 1);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(SZ + MemoryAllocator::HUGEPAGE_SIZE, buf.size());
    EXPECT_TRUE(buf.resize_inplace(SZ/2 + 1));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(SZ, buf.size());
    EXPECT_FALSE(buf.resize_inplace(SZ/2));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(SZ, buf.size());
    EXPECT_TRUE(buf.resize_inplace(SZ));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(SZ, buf.size());
}

TEST_MAIN() { TEST_RUN_ALL(); }
