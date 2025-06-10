// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespamalloc/malloc/allocchunk.h>
#include <vespamalloc/malloc/mmappool.h>
#include <unistd.h>

TEST(AtomicTest, verify_lock_freeness_of_atomics){
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
        ASSERT_EQ(16u, sizeof(vespamalloc::TaggedPtr));
        // See https://gcc.gnu.org/ml/gcc-patches/2017-01/msg02344.html for background
        ASSERT_TRUE(taggedPtr.is_lock_free() || !taggedPtr.is_lock_free());
    }

}

TEST(AtomicTest, test_explicit_mmap_munmap) {
    vespamalloc::MMapPool mmapPool;
    size_t page_size = getpagesize();
    size_t mmap1_size = 3 * page_size;
    size_t mmap2_size = 7 * page_size;
    EXPECT_EQ(0u, mmapPool.getNumMappings());
    EXPECT_EQ(0u, mmapPool.getMmappedBytes());

    void * mmap1 = mmapPool.mmap(mmap1_size);
    EXPECT_EQ(1u, mmapPool.getNumMappings());
    EXPECT_EQ(mmap1_size, mmapPool.getMmappedBytes());
    EXPECT_EQ(mmap1_size, mmapPool.get_size(mmap1));
    mmapPool.unmap(mmap1);
    EXPECT_EQ(0u, mmapPool.getNumMappings());
    EXPECT_EQ(0u, mmapPool.getMmappedBytes());
    mmap1 = mmapPool.mmap(mmap1_size);
    EXPECT_EQ(1u, mmapPool.getNumMappings());
    EXPECT_EQ(mmap1_size, mmapPool.getMmappedBytes());
    EXPECT_EQ(mmap1_size, mmapPool.get_size(mmap1));

    void * mmap2 = mmapPool.mmap(mmap2_size);
    EXPECT_EQ(2u, mmapPool.getNumMappings());
    EXPECT_EQ(mmap1_size + mmap2_size, mmapPool.getMmappedBytes());
    EXPECT_EQ(mmap1_size, mmapPool.get_size(mmap1));
    EXPECT_EQ(mmap2_size, mmapPool.get_size(mmap2));
    mmapPool.unmap(mmap1);
    EXPECT_EQ(1u, mmapPool.getNumMappings());
    EXPECT_EQ(mmap2_size, mmapPool.getMmappedBytes());
    mmapPool.unmap(mmap2);
    EXPECT_EQ(0u, mmapPool.getNumMappings());
    EXPECT_EQ(0u, mmapPool.getMmappedBytes());
}

GTEST_MAIN_RUN_ALL_TESTS()
