// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/round_up_to_page_size.h>
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
    EXPECT_EQ(page_sz, a.size());
    EXPECT_EQ(2 * page_sz, b.size());
    std::swap(a, b);
    EXPECT_EQ(page_sz, b.size());
    EXPECT_EQ(2 * page_sz, a.size());
    EXPECT_EQ(tmpA, b.get());
    EXPECT_EQ(tmpB, a.get());
}

TEST(AllocTest, test_roundUp2inN) {
    EXPECT_EQ(0u, roundUp2inN(0));
    EXPECT_EQ(2u, roundUp2inN(1));
    EXPECT_EQ(2u, roundUp2inN(2));
    EXPECT_EQ(4u, roundUp2inN(3));
    EXPECT_EQ(4u, roundUp2inN(4));
    EXPECT_EQ(8u, roundUp2inN(5));
    EXPECT_EQ(8u, roundUp2inN(6));
    EXPECT_EQ(8u, roundUp2inN(7));
    EXPECT_EQ(8u, roundUp2inN(8));
    EXPECT_EQ(16u, roundUp2inN(9));
}

TEST(AllocTest, test_roundUp2inN_elems) {
    EXPECT_EQ(0u, roundUp2inN(0, 17));
    EXPECT_EQ(1u, roundUp2inN(1, 17));
    EXPECT_EQ(3u, roundUp2inN(2, 17));
    EXPECT_EQ(3u, roundUp2inN(3, 17));
    EXPECT_EQ(7u, roundUp2inN(4, 17));
    EXPECT_EQ(7u, roundUp2inN(5, 17));
    EXPECT_EQ(7u, roundUp2inN(6, 17));
    EXPECT_EQ(7u, roundUp2inN(7, 17));
    EXPECT_EQ(15u, roundUp2inN(8, 17));
    EXPECT_EQ(15u, roundUp2inN(9, 17));
    EXPECT_EQ(15u, roundUp2inN(15, 17));
    EXPECT_EQ(30u, roundUp2inN(16, 17));
}

TEST(AllocTest, test_basics) {
    {
        Alloc h = Alloc::allocHeap(100);
        EXPECT_EQ(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        VESPA_EXPECT_EXCEPTION(Alloc::allocAlignedHeap(100, 7), IllegalArgumentException, "Alloc::allocAlignedHeap(100, 7) does not support 7 alignment");
        Alloc h = Alloc::allocAlignedHeap(100, 1_Ki);
        EXPECT_EQ(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        Alloc h = Alloc::allocMMap(100);
        EXPECT_EQ(page_sz, h.size());
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

TEST(AllocTest, test_correct_alignment) {
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

TEST(AllocTest, no_rounding_of_small_heap_buffer) {
    Alloc buf = Alloc::alloc(3, MemoryAllocator::HUGEPAGE_SIZE);
    EXPECT_EQ(3ul, buf.size());
}

TEST(AllocTest, no_rounding_of_large_heap_buffer) {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE*11+3, MemoryAllocator::HUGEPAGE_SIZE*16);
    EXPECT_EQ(size_t(MemoryAllocator::HUGEPAGE_SIZE*11+3), buf.size());
}

TEST(AllocTest, rounding_of_small_mmaped_buffer) {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE);
    EXPECT_EQ(MemoryAllocator::HUGEPAGE_SIZE, buf.size());
    buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE+1);
    EXPECT_EQ(MemoryAllocator::HUGEPAGE_SIZE*2ul, buf.size());
}

TEST(AllocTest, rounding_of_large_mmaped_buffer) {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE*11+3);
    EXPECT_EQ(MemoryAllocator::HUGEPAGE_SIZE*12ul, buf.size());
}

void verifyExtension(Alloc& buf, size_t currSZ, size_t newSZ) {
    bool expectSuccess = (currSZ != newSZ);
    void* oldPtr = buf.get();
    EXPECT_EQ(currSZ, buf.size());
    EXPECT_EQ(expectSuccess, buf.resize_inplace(currSZ + 1));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(newSZ, buf.size());
}

TEST(AllocTest, heap_alloc_can_not_be_extended) {
    Alloc buf = Alloc::allocHeap(100);
    verifyExtension(buf, 100, 100);
}

TEST(AllocTest, mmap_alloc_cannot_be_extended_from_zero) {
    Alloc buf = Alloc::allocMMap(0);
    verifyExtension(buf, 0, 0);
}

TEST(AllocTest, auto_alloced_heap_alloc_can_not_be_extended) {
    Alloc buf = Alloc::alloc(100);
    verifyExtension(buf, 100, 100);
}

TEST(AllocTest, auto_alloced_heap_alloc_can_not_be_extended__even_if_resize_will_be_mmapped) {
    Alloc buf = Alloc::alloc(100);
    void * oldPtr = buf.get();
    EXPECT_EQ(100ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(MemoryAllocator::HUGEPAGE_SIZE*3));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(100ul, buf.size());
}

void ensureRoomForExtension(const Alloc & buf, Alloc & reserved) {
    // Normally mmapping starts at the top and grows down in address space.
    // Then there is no room to extend the last mapping.
    // So in order to verify this we first mmap a reserved area that we unmap
    // before we test extension.
    if (reserved.get() > buf.get()) {
        EXPECT_EQ(reserved.get(), static_cast<const void *>(static_cast<const char *>(buf.get()) + buf.size()));
        {
            Alloc().swap(reserved);
        }
    }
}

void verifyNoExtensionWhenNoRoom(Alloc & buf, Alloc & reserved, size_t sz) {
    if (reserved.get() > buf.get()) {
        // Normally mmapping starts at the top and grows down in address space.
        // Then there is no room to extend the last mapping.
        EXPECT_EQ(reserved.get(), static_cast<const void *>(static_cast<const char *>(buf.get()) + buf.size()));
        GTEST_DO(verifyExtension(buf, sz, sz));
    } else {
        EXPECT_EQ(buf.get(), static_cast<const void *>(static_cast<const char *>(reserved.get()) + reserved.size()));
        GTEST_DO(verifyExtension(reserved, sz, sz));
    }
}

#ifdef __linux__
/*
 * The two following tests are disabled when any sanitizer is
 * enabled since extra instrumentation code might trigger extra mmap
 * or munmap calls, breaking some of the assumptions in the disabled
 * tests.
 */
#ifndef VESPA_USE_SANITIZER
TEST(AllocTest, mmap_alloc_can_be_extended_if_room) {
    Alloc dummy = Alloc::allocMMap(100);
    Alloc reserved = Alloc::allocMMap(100);
    Alloc buf = Alloc::allocMMap(100);

    GTEST_DO(ensureRoomForExtension(buf, reserved));
    GTEST_DO(verifyExtension(buf, page_sz, page_sz * 2));
}

TEST(AllocTest, mmap_alloc_can_not_be_extended_if_no_room) {
    Alloc dummy = Alloc::allocMMap(100);
    Alloc reserved = Alloc::allocMMap(100);
    Alloc buf = Alloc::allocMMap(100);

    GTEST_DO(verifyNoExtensionWhenNoRoom(buf, reserved, page_sz));
}
#endif
#endif

TEST(AllocTest, heap_alloc_can_not_be_shrinked) {
    Alloc buf = Alloc::allocHeap(101);
    void * oldPtr = buf.get();
    EXPECT_EQ(101ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(100));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(101ul, buf.size());
}

TEST(AllocTest, heap_alloc_cannot_be_shrunk_to_zero) {
    Alloc buf = Alloc::allocHeap(101);
    EXPECT_FALSE(buf.resize_inplace(0));
}

TEST(AllocTest, mmap_alloc_can_be_shrinked) {
    Alloc buf = Alloc::allocMMap(page_sz + 1);
    void * oldPtr = buf.get();
    EXPECT_EQ(2 * page_sz, buf.size());
    EXPECT_TRUE(buf.resize_inplace(page_sz - 1));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(page_sz, buf.size());
}

TEST(AllocTest, mmap_alloc_cannot_be_shrunk_to_zero) {
    Alloc buf = Alloc::allocMMap(page_sz + 1);
    EXPECT_FALSE(buf.resize_inplace(0));
}

TEST(AllocTest, auto_alloced_heap_alloc_can_not_be_shrinked) {
    Alloc buf = Alloc::alloc(101);
    void * oldPtr = buf.get();
    EXPECT_EQ(101ul, buf.size());
    EXPECT_FALSE(buf.resize_inplace(100));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(101ul, buf.size());
}

TEST(AllocTest, auto_alloced_heap_alloc_cannot_be_shrunk_to_zero) {
    Alloc buf = Alloc::alloc(101);
    EXPECT_FALSE(buf.resize_inplace(0));
}

TEST(AllocTest, auto_alloced_mmap_alloc_can_be_shrinked) {
    static constexpr size_t SZ = MemoryAllocator::HUGEPAGE_SIZE;
    Alloc buf = Alloc::alloc(SZ + 1);
    void * oldPtr = buf.get();
    EXPECT_EQ(SZ + MemoryAllocator::HUGEPAGE_SIZE, buf.size());
    EXPECT_TRUE(buf.resize_inplace(SZ-1));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(SZ, buf.size());
}

TEST(AllocTest, auto_alloced_mmap_alloc_cannot_be_shrunk_to_zero) {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE + 1);
    EXPECT_FALSE(buf.resize_inplace(0));
}

TEST(AllocTest, auto_alloced_mmap_alloc_can_not_be_shrinked_below_HUGEPAGE_SIZE_div_2_plus_1) {
    static constexpr size_t SZ = MemoryAllocator::HUGEPAGE_SIZE;
    Alloc buf = Alloc::alloc(SZ + 1);
    void * oldPtr = buf.get();
    EXPECT_EQ(SZ + MemoryAllocator::HUGEPAGE_SIZE, buf.size());
    EXPECT_TRUE(buf.resize_inplace(SZ/2 + 1));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(SZ, buf.size());
    EXPECT_FALSE(buf.resize_inplace(SZ/2));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(SZ, buf.size());
    EXPECT_TRUE(buf.resize_inplace(SZ));
    EXPECT_EQ(oldPtr, buf.get());
    EXPECT_EQ(SZ, buf.size());
}

GTEST_MAIN_RUN_ALL_TESTS()
