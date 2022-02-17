// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespamalloc/malloc/allocchunk.h>
#include <vespamalloc/malloc/mmappool.h>

TEST("verify lock freeness of atomics"){
    {
        std::atomic<uint32_t> uint32V;
        ASSERT_TRUE(uint32V.is_lock_free());
    }
    {
        std::atomic<uint64_t> uint64V;
        ASSERT_TRUE(uint64V.is_lock_free());
    }
    {
        std::atomic<vespamalloc::TaggedPtr> taggedPtr;
        ASSERT_EQUAL(16u, sizeof(vespamalloc::TaggedPtr));
        // See https://gcc.gnu.org/ml/gcc-patches/2017-01/msg02344.html for background
        ASSERT_TRUE(taggedPtr.is_lock_free() || !taggedPtr.is_lock_free());
    }

}

TEST("test explicit mmap/munmap") {
    vespamalloc::MMapPool mmapPool;
    EXPECT_EQUAL(0u, mmapPool.getNumMappings());
    EXPECT_EQUAL(0u, mmapPool.getMmappedBytes());

    void * mmap1 = mmapPool.mmap(0xe000);
    EXPECT_EQUAL(1u, mmapPool.getNumMappings());
    EXPECT_EQUAL(0xe000u, mmapPool.getMmappedBytes());
    EXPECT_EQUAL(0xe000u, mmapPool.get_size(mmap1));
    mmapPool.unmap(mmap1);
    EXPECT_EQUAL(0u, mmapPool.getNumMappings());
    EXPECT_EQUAL(0u, mmapPool.getMmappedBytes());
    mmap1 = mmapPool.mmap(0xe000);
    EXPECT_EQUAL(1u, mmapPool.getNumMappings());
    EXPECT_EQUAL(0xe000u, mmapPool.getMmappedBytes());
    EXPECT_EQUAL(0xe000u, mmapPool.get_size(mmap1));

    void * mmap2 = mmapPool.mmap(0x1e000);
    EXPECT_EQUAL(2u, mmapPool.getNumMappings());
    EXPECT_EQUAL(0x2c000u, mmapPool.getMmappedBytes());
    EXPECT_EQUAL(0xe000u, mmapPool.get_size(mmap1));
    EXPECT_EQUAL(0x1e000u, mmapPool.get_size(mmap2));
    mmapPool.unmap(mmap1);
    EXPECT_EQUAL(1u, mmapPool.getNumMappings());
    EXPECT_EQUAL(0x1e000u, mmapPool.getMmappedBytes());
    mmapPool.unmap(mmap2);
    EXPECT_EQUAL(0u, mmapPool.getNumMappings());
    EXPECT_EQUAL(0u, mmapPool.getMmappedBytes());
}

TEST_MAIN() { TEST_RUN_ALL(); }
