// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <stddef.h>
#include <vespa/log/log.h>
LOG_SETUP("alloc_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib;
using namespace vespalib::alloc;

template <typename T>
void
testSwap(T & a, T & b)
{
    void * tmpA(a.get());
    void * tmpB(b.get());
    EXPECT_EQUAL(100u, a.size());
    EXPECT_EQUAL(200u, b.size());
    std::swap(a, b);
    EXPECT_EQUAL(100u, b.size());
    EXPECT_EQUAL(200u, a.size());
    EXPECT_EQUAL(tmpA, b.get());
    EXPECT_EQUAL(tmpB, a.get());
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
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        Alloc a = Alloc::allocHeap(100), b = Alloc::allocHeap(200);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocMMap(100), b = Alloc::allocMMap(200);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocAlignedHeap(100, 1024), b = Alloc::allocAlignedHeap(200, 1024);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocHeap(100);
        Alloc b = Alloc::allocMMap(200);
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
    EXPECT_EQUAL(MemoryAllocator::HUGEPAGE_SIZE*11+3, buf.size());
}

TEST("rounding of small mmaped buffer") {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE);
    EXPECT_EQUAL(MemoryAllocator::HUGEPAGE_SIZE, buf.size());
    buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE+1);
    EXPECT_EQUAL(MemoryAllocator::HUGEPAGE_SIZE*2, buf.size());
}

TEST("rounding of large mmaped buffer") {
    Alloc buf = Alloc::alloc(MemoryAllocator::HUGEPAGE_SIZE*11+3);
    EXPECT_EQUAL(MemoryAllocator::HUGEPAGE_SIZE*12, buf.size());
}

TEST("heap alloc can not be extended") {
   Alloc buf = Alloc::allocHeap(100);
   void * oldPtr = buf.get();
   size_t oldSz = buf.size();
   EXPECT_FALSE(buf.extend_inplace(101));
   EXPECT_EQUAL(oldPtr, buf.get());
   EXPECT_EQUAL(oldSz, buf.size());
}


TEST_MAIN() { TEST_RUN_ALL(); }
