// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/process_memory_stats.h>
#include <vespa/vespalib/util/size_literals.h>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <fcntl.h>
#include <sys/mman.h>

using namespace vespalib;

class ProcessMemoryStatsTest : public ::testing::Test
{
protected:
    static void SetUpTestSuite() {
        std::filesystem::remove("mapfile");
    }

    static void TearDownTestSuite() {
        std::filesystem::remove("mapfile");
    }

    static constexpr double SIZE_EPSILON = 0.01;

    static std::string toString(const ProcessMemoryStats &stats)
    {
        std::ostringstream os;
        os << "Virtual("
           << stats.getVirt() <<
           "), Rss("
           << stats.getMappedRss() + stats.getAnonymousRss() <<
           "), MappedRss("
           << stats.getMappedRss() <<
           "), AnonymousRss("
           << stats.getAnonymousRss() << ")";
        return os.str();
    }

};

TEST_F(ProcessMemoryStatsTest, simple_stats)
{
    ProcessMemoryStats stats(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats) << std::endl;
    EXPECT_LT(0u, stats.getVirt());
    EXPECT_LT(0u, stats.getMappedRss());
    EXPECT_LT(0u, stats.getAnonymousRss());
}

TEST_F(ProcessMemoryStatsTest, grow_anonymous_memory)
{
    ProcessMemoryStats stats1(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats1) << std::endl;
    size_t mapLen = 64_Ki;
    void *mapAddr = mmap(nullptr, mapLen, PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    EXPECT_NE(reinterpret_cast<void *>(-1), mapAddr);
    ProcessMemoryStats stats2(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats2) << std::endl;
    EXPECT_LE(stats1.getVirt() + mapLen,
              stats2.getVirt());
    memset(mapAddr, 1, mapLen);
    ProcessMemoryStats stats3(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats3) << std::endl;
    // Cannot check that resident grows if swap is enabled and system loaded
    munmap(mapAddr, mapLen);
}

TEST_F(ProcessMemoryStatsTest, grow_mapped_memory)
{
    std::ofstream of("mapfile");
    size_t mapLen = 64_Ki;
    std::vector<char> buf(mapLen, 4);
    of.write(&buf[0], buf.size());
    of.close();
    int mapfileFileDescriptor = open("mapfile", O_RDONLY, 0666);
    EXPECT_LE(0, mapfileFileDescriptor);
    ProcessMemoryStats stats1(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats1) << std::endl;
    void *mapAddr = mmap(nullptr, mapLen, PROT_READ, MAP_SHARED,
                         mapfileFileDescriptor, 0);
    EXPECT_NE(reinterpret_cast<void *>(-1), mapAddr);
    ProcessMemoryStats stats2(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats2) << std::endl;
    EXPECT_LE(stats1.getVirt() + mapLen, stats2.getVirt());
    EXPECT_EQ(0, memcmp(mapAddr, &buf[0], mapLen));
    ProcessMemoryStats stats3(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats3) << std::endl;
    // Cannot check that resident grows if swap is enabled and system loaded
    munmap(mapAddr, mapLen);
}

TEST_F(ProcessMemoryStatsTest, order_samples)
{
    ProcessMemoryStats a(0,0,7);
    ProcessMemoryStats b(0,0,8);
    EXPECT_TRUE(a < b);
    EXPECT_FALSE(b < a);
}

TEST_F(ProcessMemoryStatsTest, parse_statm)
{
    // size resident shared text lib data dt
    std::string statm = "3332000 1917762 8060 1 0 2960491 0";
    std::string_view view(statm);
    asciistream stream(view);

    ProcessMemoryStats stats = ProcessMemoryStats::parseStatm(stream);
    EXPECT_EQ(3332000 * ProcessMemoryStats::PAGE_SIZE, stats.getVirt());
    EXPECT_EQ((1917762 - 8060) * ProcessMemoryStats::PAGE_SIZE, stats.getAnonymousRss());
    EXPECT_EQ(8060 * ProcessMemoryStats::PAGE_SIZE, stats.getMappedRss());
}

GTEST_MAIN_RUN_ALL_TESTS()
