// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("alignedmemory_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/alignedmemory.h>

using namespace vespalib;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("alignedmemory_test");
    { // aligned alloc
        AlignedMemory mem8(32, 8);
        AlignedMemory mem16(32, 16);
        AlignedMemory mem512(32, 512);
        AlignedMemory mem7(32, 7);

        EXPECT_EQUAL(0u, ((uintptr_t)mem8.get()) % 8);
        EXPECT_EQUAL(0u, ((uintptr_t)mem16.get()) % 16);
        EXPECT_EQUAL(0u, ((uintptr_t)mem512.get()) % 512);
        EXPECT_EQUAL(0u, ((uintptr_t)mem7.get()) % 7);
    }
    { // swap
        AlignedMemory a(32, 8);
        AlignedMemory b(32, 8);
        char *pa = a.get();
        char *pb = b.get();

        EXPECT_EQUAL(pa, a.get());
        EXPECT_EQUAL(pb, b.get());
        a.swap(b);
        EXPECT_EQUAL(pb, a.get());
        EXPECT_EQUAL(pa, b.get());
        b.swap(a);
        EXPECT_EQUAL(pa, a.get());
        EXPECT_EQUAL(pb, b.get());
    }
    { // std::swap
        AlignedMemory a(32, 8);
        AlignedMemory b(32, 8);
        char *pa = a.get();
        char *pb = b.get();

        EXPECT_EQUAL(pa, a.get());
        EXPECT_EQUAL(pb, b.get());
        std::swap(a, b);
        EXPECT_EQUAL(pb, a.get());
        EXPECT_EQUAL(pa, b.get());
        std::swap(a, b);
        EXPECT_EQUAL(pa, a.get());
        EXPECT_EQUAL(pb, b.get());
    }
    { // construct with zero size
        AlignedMemory null(0, 0);
        char *expect = 0;
        EXPECT_EQUAL(expect, null.get());
    }
    { // const get()
        const AlignedMemory null(0, 0);
        const char *expect = 0;
        const char *got = null.get();
        EXPECT_EQUAL(expect, got);
    }
    TEST_DONE();
}
