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

class Test : public TestApp
{
public:
    int Main();
    void testAlignedAllocation();
    void testBasic();
    template <typename T>
    void testSwap(T & a, T & b);
};

int
Test::Main()
{
    TEST_INIT("alloc_test");
    testBasic();
    testAlignedAllocation();
    TEST_DONE();
}

template <typename T>
void
Test::testSwap(T & a, T & b)
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

void
Test::testBasic()
{
    {
        Alloc h = Alloc::allocHeap(100);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        EXPECT_EXCEPTION(Alloc::allocAlignedHeap(100, 7), IllegalArgumentException, "Alloc::allocAlignedHeapAlloc(100, 7) does not support 7 alignment");
        Alloc h = Alloc::allocAlignedHeapAlloc(100, 1024);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        Alloc h = MMapAllocFactory::create(100);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != nullptr);
    }
    {
        Alloc a = Alloc::allocHeap(100), b = Alloc::allocHeap(200);
        testSwap(a, b);
    }
    {
        Alloc a = MMapAllocFactory::create(100), b = MMapAllocFactory::create(200);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocAlignedHeap(100, 1024), b = Alloc::allocAlignedHeap(200, 1024);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocHeap(100);
        Alloc b = MMapAllocFactory::create(200);
        testSwap(a, b);
    }
    {
        Alloc a = Alloc::allocHeap(100);
        Alloc b = Alloc::allocHeap(100);
        a = std::move(b);
        EXPECT_TRUE(b.get() == nullptr);
    }
}

void
Test::testAlignedAllocation()
{
    {
        Alloc buf = AutoAllocFactory::create(10, MemoryAllocator::HUGEPAGE_SIZE, 1024);
        EXPECT_TRUE(reinterpret_cast<ptrdiff_t>(buf.get()) % 1024 == 0);
    }

    {
        // Mmapped pointers are page-aligned, but sanity test anyway.
        Alloc buf = AutoAllocFactory::create(3000000, MemoryAllocator::HUGEPAGE_SIZE, 512);
        EXPECT_TRUE(reinterpret_cast<ptrdiff_t>(buf.get()) % 512 == 0);
    }
}

TEST_APPHOOK(Test)
