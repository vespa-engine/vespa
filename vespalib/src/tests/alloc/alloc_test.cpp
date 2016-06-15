// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <stddef.h>
#include <vespa/log/log.h>
LOG_SETUP("alloc_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib;

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
    swap(a, b);
    EXPECT_EQUAL(100u, b.size());
    EXPECT_EQUAL(200u, a.size());
    EXPECT_EQUAL(tmpA, b.get());
    EXPECT_EQUAL(tmpB, a.get());
}

void
Test::testBasic()
{
    {
        HeapAlloc h(100);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != NULL);
    }
    {
        EXPECT_EXCEPTION(AlignedHeapAlloc(100, 0), IllegalArgumentException, "posix_memalign(100, 0) failed with code 22");
        AlignedHeapAlloc h(100, 1024);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != NULL);
    }
    {
        MMapAlloc h(100);
        EXPECT_EQUAL(100u, h.size());
        EXPECT_TRUE(h.get() != NULL);
    }
    {
        HeapAlloc a(100), b(200);
        testSwap(a, b);
    }
    {
        MMapAlloc a(100), b(200);
        testSwap(a, b);
    }
    {
        AlignedHeapAlloc a(100, 1024), b(200, 1024);
        testSwap(a, b);
    }
}

void
Test::testAlignedAllocation()
{
    {
        AutoAlloc<2048, 1024> buf(10);
        EXPECT_TRUE(reinterpret_cast<ptrdiff_t>(buf.get()) % 1024 == 0);
    }

    {
        // Mmapped pointers are page-aligned, but sanity test anyway.
        AutoAlloc<1024, 512> buf(3000);
        EXPECT_TRUE(reinterpret_cast<ptrdiff_t>(buf.get()) % 512 == 0);
    }
}

TEST_APPHOOK(Test)
