// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    template<typename T>
    void testPopCount();
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

template<typename T>
void Test::testPopCount()
{
    EXPECT_EQUAL(0, Optimized::popCount(T(0)));
    EXPECT_EQUAL(1, Optimized::popCount(T(1)));
    EXPECT_EQUAL(int(8 * sizeof(T)), Optimized::popCount(T(-1)));
}

int Test::Main()
{
    TEST_INIT("optimized_test");

    testMsbIdx<unsigned int>();
    testMsbIdx<unsigned long>();
    testMsbIdx<unsigned long long>();

    TEST_FLUSH();
    testLsbIdx<unsigned int>();
    testLsbIdx<unsigned long>();
    testLsbIdx<unsigned long long>();

    TEST_FLUSH();
    testPopCount<unsigned int>();
    testPopCount<unsigned long>();
    testPopCount<unsigned long long>();

    TEST_FLUSH();
    TEST_DONE();
}

TEST_APPHOOK(Test)
