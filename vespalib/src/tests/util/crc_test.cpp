// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/crc.h>
#include <vector>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

TEST(FCrcTest, test_correctness)
{
    const char *a[7] = { "", "a", "ab", "abc", "abcd", "abcde", "doc:crawler:http://www.ntnu.no/" };
    uint32_t expected[7] = {0, 0xe8b7be43, 0x9e83486d, 0x352441c2, 0xed82cd11, 0x8587d865, 0x86287fc5};
    for (size_t i(0); i < sizeof(a)/sizeof(a[0]); i++) {
        uint32_t vespaCrc32 = crc_32_type::crc(a[i], strlen(a[i]));
        EXPECT_EQ(vespaCrc32, expected[i]);
        vespalib::crc_32_type calculator2;
        calculator2.process_bytes(a[i], strlen(a[i]));
        EXPECT_EQ(vespaCrc32, calculator2.checksum());
    }
    vespalib::crc_32_type calculator;
    uint32_t accum_expected[7] = {0, 0xe8b7be43, 0x690e2297, 0x8d7284f9, 0x7ed0c389, 0x61bc2a26, 0x1816e339};
    for (size_t i(0); i < sizeof(a)/sizeof(a[0]); i++) {
        calculator.process_bytes(a[i], strlen(a[i]));
        EXPECT_EQ(calculator.checksum(), accum_expected[i]);
    }
}

TEST(CrcTest, benchmark)
{
    constexpr size_t bufSz = 1024;
    constexpr size_t numRep = 100 * 1000;
    std::vector<char> a(numRep+bufSz);
    for(size_t i(0), m(a.size()); i < m; i++) {
        a[i] = i&0xff;
    }
    uint32_t sum(0);
    for (size_t i(0); i < (numRep); i++) {
        //sum ^= crc_32_type::crc(&a[i], bufSz);
        vespalib::crc_32_type calculator;
        calculator.process_bytes(&a[i], bufSz);
        sum ^=calculator.checksum();
    }
    printf("sum = %x\n", sum);
}
