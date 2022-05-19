// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/bits.h>
#include <boost/crc.hpp>
#include <boost/version.hpp>

#if BOOST_VERSION < 106900
    #define REFLECT reflect
#else
    #define REFLECT reflect_q
#endif

using namespace vespalib;

class Test : public TestApp
{
public:
    int Main() override;
    template <typename T>
    void testFixed(const T * v, size_t sz);
    void testBuffer();
};

int
Test::Main()
{
    TEST_INIT("bits_test");
    uint8_t u8[5] = { 0, 1, 127, 135, 255 };
    testFixed(u8, sizeof(u8)/sizeof(u8[0]));
    uint16_t u16[5] = { 0, 1, 127, 135, 255 };
    testFixed(u16, sizeof(u16)/sizeof(u16[0]));
    uint32_t u32[5] = { 0, 1, 127, 135, 255 };
    testFixed(u32, sizeof(u32)/sizeof(u32[0]));
    uint64_t u64[5] = { 0, 1, 127, 135, 255 };
    testFixed(u64, sizeof(u64)/sizeof(u64[0]));
    testBuffer();
    TEST_DONE();
}

template <typename T>
void Test::testFixed(const T * v, size_t sz)
{
    EXPECT_EQUAL(0u, Bits::reverse(static_cast<T>(0)));
    EXPECT_EQUAL(1ul << (sizeof(T)*8 - 1), Bits::reverse(static_cast<T>(1)));
    EXPECT_EQUAL(static_cast<T>(-1), Bits::reverse(static_cast<T>(-1)));
    for (size_t i(0); i < sz; i++) {
        EXPECT_EQUAL(Bits::reverse(v[i]), boost::detail::reflector<sizeof(T)*8>::REFLECT(v[i]));
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
