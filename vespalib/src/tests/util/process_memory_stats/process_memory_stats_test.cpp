// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/process_memory_stats.h>
#include <vespa/vespalib/util/size_literals.h>
#include <iostream>
#include <fstream>
#include <sys/mman.h>
#include <fcntl.h>

using namespace vespalib;

namespace {

constexpr double SIZE_EPSILON = 0.01;

std::string toString(const ProcessMemoryStats &stats)
{
    std::ostringstream os;
    os << "Mapped("
       << stats.getMappedVirt() << "," << stats.getMappedRss() <<
        "), Anonymous("
       << stats.getAnonymousVirt() << "," << stats.getAnonymousRss() << ")";
    return os.str();
}

}

TEST("Simple stats")
{
    ProcessMemoryStats stats(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats) << std::endl;
    EXPECT_LESS(0u, stats.getMappedVirt());
    EXPECT_LESS(0u, stats.getMappedRss());
    EXPECT_LESS(0u, stats.getAnonymousVirt());
    EXPECT_LESS(0u, stats.getAnonymousRss());
}

TEST("grow anonymous memory")
{
    ProcessMemoryStats stats1(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats1) << std::endl;
    size_t mapLen = 64_Ki;
    void *mapAddr = mmap(nullptr, mapLen, PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    EXPECT_NOT_EQUAL(reinterpret_cast<void *>(-1), mapAddr);
    ProcessMemoryStats stats2(ProcessMemoryStats::create(0.01));
    std::cout << toString(stats2) << std::endl;
    EXPECT_LESS_EQUAL(stats1.getAnonymousVirt() + mapLen,
                      stats2.getAnonymousVirt());
    memset(mapAddr, 1, mapLen);
    ProcessMemoryStats stats3(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats3) << std::endl;
    // Cannot check that resident grows if swap is enabled and system loaded
    munmap(mapAddr, mapLen);
}

TEST("grow mapped memory")
{
    std::ofstream of("mapfile");
    size_t mapLen = 64_Ki;
    std::vector<char> buf(mapLen, 4);
    of.write(&buf[0], buf.size());
    of.close();
    int mapfileFileDescriptor = open("mapfile", O_RDONLY, 0666);
    EXPECT_LESS_EQUAL(0, mapfileFileDescriptor);
    ProcessMemoryStats stats1(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats1) << std::endl;
    void *mapAddr = mmap(nullptr, mapLen, PROT_READ, MAP_SHARED,
                         mapfileFileDescriptor, 0);
    EXPECT_NOT_EQUAL(reinterpret_cast<void *>(-1), mapAddr);
    ProcessMemoryStats stats2(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats2) << std::endl;
    EXPECT_LESS_EQUAL(stats1.getMappedVirt() + mapLen, stats2.getMappedVirt());
    EXPECT_EQUAL(0, memcmp(mapAddr, &buf[0], mapLen));
    ProcessMemoryStats stats3(ProcessMemoryStats::create(SIZE_EPSILON));
    std::cout << toString(stats3) << std::endl;
    // Cannot check that resident grows if swap is enabled and system loaded
    munmap(mapAddr, mapLen);
}

TEST("order samples")
{
    ProcessMemoryStats a(0,0,0,7,0);
    ProcessMemoryStats b(0,0,0,8,0);
    EXPECT_TRUE(a < b);
    EXPECT_FALSE(b < a);
}

TEST_MAIN() { TEST_RUN_ALL(); }
