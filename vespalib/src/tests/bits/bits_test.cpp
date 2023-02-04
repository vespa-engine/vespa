// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/bits.h>

using namespace vespalib;

class Test : public TestApp
{
public:
    int Main() override;
    template <typename T>
    void testFixed(const T * v, size_t sz, const T * exp);
    void testBuffer();
};

int
Test::Main()
{
    TEST_INIT("bits_test");
    uint8_t u8[5] = { 0, 0x1, 0x7f, 0x87, 0xff };
    uint8_t exp8[5] = { 0, 0x80, 0xfe, 0xe1, 0xff };
    testFixed(u8, sizeof(u8)/sizeof(u8[0]), exp8);
    uint16_t u16[5] = { 0, 0x1, 0x7f, 0x87, 0xff };
    uint16_t exp16[5] = { 0, 0x8000, 0xfe00, 0xe100, 0xff00 };
    testFixed(u16, sizeof(u16)/sizeof(u16[0]), exp16);
    uint32_t u32[5] = { 0, 0x1, 0x7f, 0x87, 0xff };
    uint32_t exp32[5] = { 0, 0x80000000, 0xfe000000, 0xe1000000, 0xff000000 };
    testFixed(u32, sizeof(u32)/sizeof(u32[0]), exp32);
    uint64_t u64[5] = { 0, 0x1, 0x7f, 0x87, 0xff };
    uint64_t exp64[5] = { 0, 0x8000000000000000, 0xfe00000000000000, 0xe100000000000000, 0xff00000000000000 };
    testFixed(u64, sizeof(u64)/sizeof(u64[0]), exp64);
    testBuffer();
    TEST_DONE();
}

template <typename T>
void Test::testFixed(const T * v, size_t sz, const T * exp)
{
    EXPECT_EQUAL(0u, Bits::reverse(static_cast<T>(0)));
    EXPECT_EQUAL(1ul << (sizeof(T)*8 - 1), Bits::reverse(static_cast<T>(1)));
    EXPECT_EQUAL(static_cast<T>(-1), Bits::reverse(static_cast<T>(-1)));
    for (size_t i(0); i < sz; i++) {
        EXPECT_EQUAL(Bits::reverse(v[i]), exp[i]);
        EXPECT_EQUAL(Bits::reverse(Bits::reverse(v[i])), v[i]);
    }
}

void Test::testBuffer()
{
    uint64_t a(0x0102040810204080ul);
    uint64_t b(a);
    Bits::reverse(&a, sizeof(a));
    EXPECT_EQUAL(a, Bits::reverse(b));
    Bits::reverse(&a, sizeof(a));
    EXPECT_EQUAL(a, b);
}

TEST_APPHOOK(Test)
