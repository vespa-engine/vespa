// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/round_up_to_page_size.h>
#include <vespa/vespalib/util/sanitizers.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cstddef>
#include <sys/mman.h>

using namespace vespalib;
using namespace vespalib::alloc;

namespace {

size_t page_sz = round_up_to_page_size(1);

}

template <typename T>
void
testSwap(T & a, T & b)
{
    void * tmpA(a.get());
    void * tmpB(b.get());
    EXPECT_EQUAL(page_sz, a.size());
    EXPECT_EQUAL(2 * page_sz, b.size());
    std::swap(a, b);
    EXPECT_EQUAL(page_sz, b.size());
    EXPECT_EQUAL(2 * page_sz, a.size());
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

TEST("test roundUp2inN elems") {
    EXPECT_EQUAL(0u, roundUp2inN(0, 17));
    EXPECT_EQUAL(1u, roundUp2inN(1, 17));
    EXPECT_EQUAL(3u, roundUp2inN(2, 17));
    EXPECT_EQUAL(3u, roundUp2inN(3, 17));
    EXPECT_EQUAL(7u, roundUp2inN(4, 17));
    EXPECT_EQUAL(7u, roundUp2inN(5, 17));
    EXPECT_EQUAL(7u, roundUp2inN(6, 17));
    EXPECT_EQUAL(7u, roundUp2inN(7, 17));
    EXPECT_EQUAL(15u, roundUp2inN(8, 17));
    EXPECT_EQUAL(15u, roundUp2inN(9, 17));
    EXPECT_EQUAL(15u, roundUp2inN(15, 17));
    EXPECT_EQUAL(30u, roundUp2inN(16, 17));
}

TEST("test basics") {
    {
        Alloc h = Alloc::allocHeap(100);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        EXPECT_EXCEPTION(Alloc::allocAlignedHeap(100, 7), IllegalArgumentException, "Alloc::allocAlignedHeap(100, 7) does not support 7 alignment");
        Alloc h = Alloc::allocAlignedHeap(100, 1_Ki);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        Alloc h = Alloc::allocMMap(100);
        EXPECT_EQUAL(page_sz, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        Alloc a = Alloc::allocHeap(page_sz), b = Alloc::allocHeap(2 * page_sz);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocMMap(page_sz), b = Alloc::allocMMap(2 * page_sz);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocAlignedHeap(page_sz, 1_Ki), b = Alloc::allocAlignedHeap(2 * page_sz, 1_Ki);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocHeap(page_sz);
        Alloc b = Alloc::allocMMap(2 * page_sz);
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
        Alloc buf = Alloc::alloc(10, MemoryAllocator::HUGEPAGE_SIZE, 1_Ki);
        EXPECT_TRUE(reinterpret_cast<ptrdiff_t>(buf.get()) % 1_Ki == 0);
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

void verifyExtension(Alloc& buf, size_t currSZ, size_t newSZ) {
    bool expectSuccess = (currSZ != newSZ);
    void* oldPtr = buf.get();
    EXPECT_EQUAL(currSZ, buf.size());
    EXPECT_EQUAL(expectSuccess, buf.resize_inplace(currSZ + 1));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(newSZ, buf.size());
}

TEST("heap alloc can not be extended") {
    Alloc buf = Alloc::allocHeap(100);
    verifyExtension(buf, 100, 100);
}

TEST("mmap alloc cannot be extended from zero") {
    Alloc buf = Alloc::allocMMap(0);
    verifyExtension(buf, 0, 0);
}

TEST("auto alloced heap alloc can not be extended") {
    Alloc buf = Alloc::alloc(100);
    verifyExtension(buf, 100, 100);
}

TEST("auto alloced heap alloc can not be extended, even if resize will be mmapped") {
    Alloc buf = Alloc::alloc(100);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(100ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(MemoryAllocator::HUGEPAGE_SIZE*3));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(100ul, buf.size());
}

void ensureRoomForExtension(const Alloc & buf, Alloc & reserved) {
    // Normally mmapping starts at the top and grows down in address space.
    // Then there is no room to extend the last mapping.
    // So in order to verify this we first mmap a reserved area that we unmap
    // before we test extension.
    if (reserved.get() > buf.get()) {
        EXPECT_EQUAL(reserved.get(), static_cast<const void *>(static_cast<const char *>(buf.get()) + buf.size()));
        {
            Alloc().swap(reserved);
        }
    }
}

void verifyNoExtensionWhenNoRoom(Alloc & buf, Alloc & reserved, size_t sz) {
    if (reserved.get() > buf.get()) {
        // Normally mmapping starts at the top and grows down in address space.
        // Then there is no room to extend the last mapping.
        EXPECT_EQUAL(reserved.get(), static_cast<const void *>(static_cast<const char *>(buf.get()) + buf.size()));
        TEST_DO(verifyExtension(buf, sz, sz));
    } else {
        EXPECT_EQUAL(buf.get(), static_cast<const void *>(static_cast<const char *>(reserved.get()) + reserved.size()));
        TEST_DO(verifyExtension(reserved, sz, sz));
    }
}

#ifdef __linux__
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

/*
 * The two following tests are disabled when address sanitizer is
 * enabled since extra instrumentation code might trigger extra mmap
 * or munmap calls, breaking some of the assumptions in the disabled
 * tests.
 */
#ifndef VESPA_USE_ADDRESS_SANITIZER
TEST("mmap alloc can be extended if room") {
    Alloc dummy = Alloc::allocMMap(100);
    Alloc reserved = Alloc::allocMMap(100);
    Alloc buf = Alloc::allocMMap(100);

    TEST_DO(ensureRoomForExtension(buf, reserved));
    TEST_DO(verifyExtension(buf, page_sz, page_sz * 2));
}

TEST("mmap alloc can not be extended if no room") {
    Alloc dummy = Alloc::allocMMap(100);
    Alloc reserved = Alloc::allocMMap(100);
    Alloc buf = Alloc::allocMMap(100);

    TEST_DO(verifyNoExtensionWhenNoRoom(buf, reserved, page_sz));
}
#endif
#endif

TEST("heap alloc can not be shrinked") {
    Alloc buf = Alloc::allocHeap(101);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(101ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(100));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(101ul, buf.size());
}

TEST("heap alloc cannot be shrunk to zero") {
    Alloc buf = Alloc::allocHeap(101);
    EXPECT_FALSE(buf.resize_inplace(0));
}

TEST("mmap alloc can be shrinked") {
    Alloc buf = Alloc::allocMMap(page_sz + 1);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(2 * page_sz, buf.size());
    EXPECT_TRUE(buf.resize_inplace(page_sz - 1));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(page_sz, buf.size());
}

TEST("mmap alloc cannot be shrunk to zero") {
    Alloc buf = Alloc::allocMMap(page_sz + 1);
    EXPECT_FALSE(buf.resize_inplace(0));
}

TEST("auto alloced heap alloc can not be shrinked") {
    Alloc buf = Alloc::alloc(101);
    void * oldPtr = buf.get();
    EXPECT_EQUAL(101ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(100));
    EXPECT_EQUAL(oldPtr, buf.get());
    EXPECT_EQUAL(101ul, buf.size());
}

TEST("auto alloced heap alloc cannot be shrunk to zero") {
    Alloc buf = Alloc::alloc(101);
    EXPECT_FALSE(buf.resize_inplace(0));
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

TEST("auto alloced mmap alloc cannot be shrunk to zero") {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE + 1);
    EXPECT_FALSE(buf.resize_inplace(0));
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
