// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib;
using compress::Integer;

class CompressTest : public TestApp
{
private:
    void verifyPositiveNumber(uint64_t n, const uint8_t * expected, size_t sz);
    void verifyNumber(int64_t n, const uint8_t * expected, size_t sz);
    void requireThatPositiveNumberCompressCorrectly();
    void requireThatNumberCompressCorrectly();
public:
    int Main() override;
};

void
CompressTest::verifyPositiveNumber(uint64_t n, const uint8_t * expected, size_t sz) {
    uint8_t buf[8];
    EXPECT_EQUAL(sz, Integer::compressPositive(n, buf));
    EXPECT_EQUAL(sz, Integer::compressedPositiveLength(n));
    for (size_t i(0); i < sz; i++) {
        EXPECT_EQUAL(expected[i], buf[i]);
    }
    EXPECT_FALSE(Integer::check_decompress_positive_space(expected, 0u));
    EXPECT_FALSE(Integer::check_decompress_positive_space(expected, sz - 1));
    EXPECT_TRUE(Integer::check_decompress_positive_space(expected, sz));
    uint64_t v(0);
    EXPECT_EQUAL(sz, Integer::decompressPositive(v, expected));
    EXPECT_EQUAL(n, v);
}

void
CompressTest::verifyNumber(int64_t n, const uint8_t * expected, size_t sz) {
    uint8_t buf[8];
    EXPECT_EQUAL(sz, Integer::compress(n, buf));
    EXPECT_EQUAL(sz, Integer::compressedLength(n));
    for (size_t i(0); i < sz; i++) {
        EXPECT_EQUAL(expected[i], buf[i]);
    }
    EXPECT_FALSE(Integer::check_decompress_space(expected, 0u));
    EXPECT_FALSE(Integer::check_decompress_space(expected, sz - 1));
    EXPECT_TRUE(Integer::check_decompress_space(expected, sz));
    int64_t v(0);
    EXPECT_EQUAL(sz, Integer::decompress(v, expected));
    EXPECT_EQUAL(n, v);
}

#define VERIFY_POSITIVE(n, p) verifyPositiveNumber(n, p, sizeof(p))
void
CompressTest::requireThatPositiveNumberCompressCorrectly()
{
    const uint8_t zero[1] = {0};
    VERIFY_POSITIVE(0, zero);
    const uint8_t one[1] = {0x01};
    VERIFY_POSITIVE(1, one);
    const uint8_t x3f[1] = {0x3f};
    VERIFY_POSITIVE(0x3f, x3f);
    const uint8_t x40[2] = {0x80,0x40};
    VERIFY_POSITIVE(0x40, x40);
    const uint8_t x3fff[2] = {0xbf, 0xff};
    VERIFY_POSITIVE(0x3fff, x3fff);
    const uint8_t x4000[4] = {0xc0, 0x00, 0x40, 0x00};
    VERIFY_POSITIVE(0x4000, x4000);
    const uint8_t x3fffffff[4] = {0xff, 0xff, 0xff, 0xff};
    VERIFY_POSITIVE(0x3fffffff, x3fffffff);
    const uint8_t x40000000[4] = {0,0,0,0};
    try {
        VERIFY_POSITIVE(0x40000000, x40000000);
        EXPECT_TRUE(false);
    } catch (const IllegalArgumentException & e)  {
        EXPECT_EQUAL("Number '1073741824' too big, must extend encoding", e.getMessage());
    }
    try {
        VERIFY_POSITIVE(-1, x40000000);
        EXPECT_TRUE(false);
    } catch (const IllegalArgumentException & e)  {
        EXPECT_EQUAL("Number '18446744073709551615' too big, must extend encoding", e.getMessage());
    }
}

#define VERIFY_NUMBER(n, p) verifyNumber(n, p, sizeof(p))
void
CompressTest::requireThatNumberCompressCorrectly()
{
    const uint8_t zero[1] = {0};
    VERIFY_NUMBER(0, zero);
    const uint8_t one[1] = {0x01};
    VERIFY_NUMBER(1, one);
    const uint8_t x1f[1] = {0x1f};
    VERIFY_NUMBER(0x1f, x1f);
    const uint8_t x20[2] = {0x40,0x20};
    VERIFY_NUMBER(0x20, x20);
    const uint8_t x1fff[2] = {0x5f, 0xff};
    VERIFY_NUMBER(0x1fff, x1fff);
    const uint8_t x2000[4] = {0x60, 0x00, 0x20, 0x00};
    VERIFY_NUMBER(0x2000, x2000);
    const uint8_t x1fffffff[4] = {0x7f, 0xff, 0xff, 0xff};
    VERIFY_NUMBER(0x1fffffff, x1fffffff);
    const uint8_t x20000000[4] = {0,0,0,0};
    try {
        VERIFY_NUMBER(0x20000000, x20000000);
        EXPECT_TRUE(false);
    } catch (const IllegalArgumentException & e)  {
        EXPECT_EQUAL("Number '536870912' too big, must extend encoding", e.getMessage());
    }
    const uint8_t mzero[1] = {0x81};
    VERIFY_NUMBER(-1, mzero);
    const uint8_t mone[1] = {0x82};
    VERIFY_NUMBER(-2, mone);
    const uint8_t mx1f[1] = {0x9f};
    VERIFY_NUMBER(-0x1f, mx1f);
    const uint8_t mx20[2] = {0xc0,0x20};
    VERIFY_NUMBER(-0x20, mx20);
    const uint8_t mx1fff[2] = {0xdf, 0xff};
    VERIFY_NUMBER(-0x1fff, mx1fff);
    const uint8_t mx2000[4] = {0xe0, 0x00, 0x20, 0x00};
    VERIFY_NUMBER(-0x2000, mx2000);
    const uint8_t mx1fffffff[4] = {0xff, 0xff, 0xff, 0xff};
    VERIFY_NUMBER(-0x1fffffff, mx1fffffff);
    const uint8_t mx20000000[4] = {0,0,0,0};
    try {
        VERIFY_NUMBER(-0x20000000, mx20000000);
        EXPECT_TRUE(false);
    } catch (const IllegalArgumentException & e)  {
        EXPECT_EQUAL("Number '-536870912' too big, must extend encoding", e.getMessage());
    }
}

int
CompressTest::Main()
{
    TEST_INIT("compress_test");

    requireThatPositiveNumberCompressCorrectly();
    requireThatNumberCompressCorrectly();

    TEST_DONE();
}

TEST_APPHOOK(CompressTest)
