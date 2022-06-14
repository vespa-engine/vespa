// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/mmap_file_allocator_factory.h>
#include <vespa/vespalib/util/mmap_file_allocator.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>

using vespalib::alloc::MemoryAllocator;
using vespalib::alloc::MmapFileAllocator;
using vespalib::alloc::MmapFileAllocatorFactory;

namespace {

vespalib::string basedir("mmap-file-allocator-factory-dir");

bool is_mmap_file_allocator(const MemoryAllocator *allocator)
{
    return dynamic_cast<const MmapFileAllocator *>(allocator) != nullptr;
}

}

TEST(MmapFileAllocatorFactoryTest, empty_dir_gives_no_allocator)
{
    MmapFileAllocatorFactory::instance().setup("");
    auto allocator = MmapFileAllocatorFactory::instance().make_memory_allocator("foo");
    EXPECT_FALSE(allocator);
}

TEST(MmapFileAllocatorFactoryTest, nonempty_dir_gives_allocator)
{
    MmapFileAllocatorFactory::instance().setup(basedir);
    auto allocator0 = MmapFileAllocatorFactory::instance().make_memory_allocator("foo");
    auto allocator1 = MmapFileAllocatorFactory::instance().make_memory_allocator("bar");
    EXPECT_TRUE(is_mmap_file_allocator(allocator0.get()));
    EXPECT_TRUE(is_mmap_file_allocator(allocator1.get()));
    vespalib::string allocator0_dir(basedir + "/0.foo");
    vespalib::string allocator1_dir(basedir + "/1.bar");
    EXPECT_TRUE(isDirectory(allocator0_dir));
    EXPECT_TRUE(isDirectory(allocator1_dir));
    allocator0.reset();
    EXPECT_FALSE(isDirectory(allocator0_dir));
    allocator1.reset();
    EXPECT_FALSE(isDirectory(allocator1_dir));
    MmapFileAllocatorFactory::instance().setup("");
    std::filesystem::remove_all(std::filesystem::path(basedir));
}

GTEST_MAIN_RUN_ALL_TESTS()
