// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/optimized.h>

using namespace vespalib;

class Test : public vespalib::TestApp
{
private:
    template<typename T>
    void testMsbIdx();
    template<typename T>
    void testLsbIdx();
public:
    int Main() override;
};

template<typename T>
void Test::testMsbIdx()
{
    EXPECT_EQUAL(Optimized::msbIdx(T(0)), 0);
    EXPECT_EQUAL(Optimized::msbIdx(T(1)), 0);
    EXPECT_EQUAL(Optimized::msbIdx(T(-1)), int(sizeof(T)*8 - 1));
    T v(static_cast<T>(-1));
    for (size_t i(0); i < sizeof(T); i++) {
        for (size_t j(0); j < 8; j++) {
            EXPECT_EQUAL(Optimized::msbIdx(v), int(sizeof(T)*8 - (i*8+j) - 1));
            v = v >> 1;
        }
    }
}

template<typename T>
void Test::testLsbIdx()
{
    EXPECT_EQUAL(Optimized::lsbIdx(T(0)), 0);
    EXPECT_EQUAL(Optimized::lsbIdx(T(1)), 0);
    EXPECT_EQUAL(Optimized::lsbIdx(T(T(1)<<(sizeof(T)*8 - 1))), int(sizeof(T)*8 - 1));
    EXPECT_EQUAL(Optimized::lsbIdx(T(-1)), 0);
    T v(static_cast<T>(-1));
    for (size_t i(0); i < sizeof(T); i++) {
        for (size_t j(0); j < 8; j++) {
            EXPECT_EQUAL(Optimized::lsbIdx(v), int(i*8+j));
            v = v << 1;
        }
    }
}

int Test::Main()
{
    TEST_INIT("optimized_test");

    testMsbIdx<uint32_t>();
    testMsbIdx<uint64_t>();

    TEST_FLUSH();
    testLsbIdx<uint32_t>();
    testLsbIdx<uint64_t>();

    TEST_FLUSH();
    EXPECT_EQUAL(Optimized::popCount(0u), 0);
    EXPECT_EQUAL(Optimized::popCount(1u), 1);
    EXPECT_EQUAL(Optimized::popCount(uint32_t(-1)), 32);
    EXPECT_EQUAL(Optimized::popCount(0ul), 0);
    EXPECT_EQUAL(Optimized::popCount(1ul), 1);
    EXPECT_EQUAL(Optimized::popCount(uint64_t(-1l)), 64);

    TEST_FLUSH();
    TEST_DONE();
}

TEST_APPHOOK(Test)
