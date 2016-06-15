// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <vespa/vdslib/container/smallvector.h>
#include <sys/time.h>

namespace storage {
namespace lib {

struct SmallVectorTest : public CppUnit::TestFixture {

    void testNormalUsage();
    void testPerformance();
    void testSwapVectorContents();
    void testErase();
    void testCopy();

    CPPUNIT_TEST_SUITE(SmallVectorTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST(testPerformance);
    CPPUNIT_TEST(testSwapVectorContents);
    CPPUNIT_TEST(testErase);
    CPPUNIT_TEST(testCopy);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SmallVectorTest);

namespace {
    template<typename T>
    inline std::ostream& operator<<(std::ostream& out,
                                    const std::vector<T>& v)
    {
        out << "[";
        for (size_t i=0; i<v.size(); ++i) {
            out << "\n  " << v[i];
        }
        if (!v.empty()) out << "\n";
        out << "]";
        return out;
    }

    template<typename T, size_t S>
    void assertEqual(const SmallVector<T, S>& sv, const std::vector<T>& v) {
        if (!(sv == v)) {
            std::ostringstream ost;
            ost << "Small vector " << sv << " is not equal to vector " << v;
            CPPUNIT_FAIL(ost.str());
        }
    }
}

void
SmallVectorTest::testNormalUsage()
{
    std::vector<uint16_t> expected;
    SmallVector<uint16_t, 8> actual;
    for (uint16_t i=0; i<16; ++i) {
        expected.push_back(i);
        actual.push_back(i);
        assertEqual(actual, expected);
    }

    SmallVector<uint16_t, 8> copy(actual);
    SmallVector<uint16_t, 16> copy2(actual);
}

namespace {

    uint64_t getCurrentTimeInMicros() {
        struct timeval mytime;
        gettimeofday(&mytime, 0);
        return mytime.tv_sec * 1000000llu + mytime.tv_usec;
    }

    template<typename IntContainer>
    struct PerformanceTestClass {
        uint32_t count;

        PerformanceTestClass(int c) : count(c) {}
        
        IntContainer getContainer(int minVal) {
            IntContainer result;
            for (uint32_t i=0; i<count; ++i) {
                result.push_back(minVal + i);
            }
            return result;
        }
    };

    template<typename IntContainer>
    uint64_t getPerformance(int containerSize) {
        uint64_t start = getCurrentTimeInMicros();
        int value = 0;
        PerformanceTestClass<IntContainer> foo(containerSize);
        for (uint32_t i=0, n=10 * 1024; i<n; ++i) {
            IntContainer ic(foo.getContainer(start));
            value += ic[0] + ic[1] - ic[2];
        }
        uint64_t stop = getCurrentTimeInMicros();
        return (stop - start);
    }

    struct ArgumentTestClass {
        uint32_t count;

        ArgumentTestClass(int c) : count(c) {}

        void getContainer(int minVal, std::vector<int>& result) {
            for (uint32_t i=0; i<count; ++i) {
                result.push_back(minVal + i);
            }
        }
    };

    uint64_t getPerformance2(int containerSize) {
        uint64_t start = getCurrentTimeInMicros();
        int value = 0;
        ArgumentTestClass foo(containerSize);
        for (uint32_t i=0, n=10 * 1024; i<n; ++i) {
            std::vector<int> ic;
            foo.getContainer(start, ic);
            value += ic[0] + ic[1] - ic[2];
        }
        uint64_t stop = getCurrentTimeInMicros();
        return (stop - start);
    }
}

void
SmallVectorTest::testPerformance()
{
    size_t low = 3;
    size_t high = 16;
    SmallVector<int> sv;

    CPPUNIT_ASSERT(low <= sv.getEfficientSizeLimit());
    CPPUNIT_ASSERT(high > sv.getEfficientSizeLimit());

    uint64_t vectorTime1 = getPerformance<std::vector<int> >(low);
    uint64_t smallVectorTime1 = getPerformance<SmallVector<int> >(low);
    uint64_t asArgTime1 = getPerformance2(low);

    uint64_t vectorTime2 = getPerformance<std::vector<int> >(high);
    uint64_t smallVectorTime2 = getPerformance<SmallVector<int> >(high);
    uint64_t asArgTime2 = getPerformance2(high);

    double factor1 = static_cast<double>(vectorTime1) / smallVectorTime1;
    double factor2 = static_cast<double>(vectorTime2) / smallVectorTime2;

    double factor3 = static_cast<double>(asArgTime1) / smallVectorTime1;
    double factor4 = static_cast<double>(asArgTime2) / smallVectorTime2;

    std::cerr << "\n"
              << "  Small vector is " << factor1
              << " x faster than std::vector with few elements\n"
              << "  Small vector is " << factor2
              << " x faster than std::vector with many elements\n"
              << "  Small vector is " << factor3
              << " x faster than std::vector as arg with few elements\n"
              << "  Small vector is " << factor4
              << " x faster than std::vector as arg with many elements\n";

    // At time of test writing, vector is ~43 times faster with small data, and
    // ~0.9 times as fast on bigger. (Without vespa malloc)
    // With vespa malloc it is about ~14 times faster.

    /* Cannot run on factory as too much other runs in parallel.
    CPPUNIT_ASSERT(factor1 > 25);
    CPPUNIT_ASSERT(factor2 > 0.5);
    // */
}

void
SmallVectorTest::testSwapVectorContents()
{
    SmallVector<uint16_t, 8> v1;
    SmallVector<uint16_t, 8> v2;

    // v1 small enough to be contained in fixed array.
    for (uint16_t i = 0; i < 6; ++i) {
        v1.push_back(i);
    }

    // v2 big enough that it needs heap backed storage.
    for (uint16_t i = 10; i < 30; ++i) {
        v2.push_back(i);
    }

    vespalib::string expectedSmall("[0, 1, 2, 3, 4, 5]");
    vespalib::string expectedBig("[10, 11, 12, 13, 14, 15, 16, 17, 18, 19, "
                                 "20, 21, 22, 23, 24, 25, 26, 27, 28, 29]");

    v1.swap(v2);

    CPPUNIT_ASSERT_EQUAL(expectedSmall, v2.toString());
    CPPUNIT_ASSERT_EQUAL(expectedBig, v1.toString());

    swap(v1, v2);
    CPPUNIT_ASSERT_EQUAL(expectedBig, v2.toString());
    CPPUNIT_ASSERT_EQUAL(expectedSmall, v1.toString());
}

void
SmallVectorTest::testErase()
{
        // Delete in small part of small array
    {
        SmallVector<uint16_t, 4> v = {3, 6, 5};
        v.erase(v.begin());
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 4>{6, 5}), v);
    }
    {
        SmallVector<uint16_t, 4> v = {3, 6, 5};
        v.erase(v.begin() + 1);
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 4>{3, 5}), v);
    }
    {
        SmallVector<uint16_t, 4> v = {3, 6, 5};
        v.erase(v.begin() + 2);
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 4>{3, 6}), v);
    }

        // Delete in small part of large array
    {
        SmallVector<uint16_t, 4> v = {3, 6, 5, 7, 8};
        v.erase(v.begin());
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 4>{6, 5, 7, 8}), v);
    }
    {
        SmallVector<uint16_t, 4> v = {3, 6, 5, 7, 8};
        v.erase(v.begin() + 1);
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 4>{3, 5, 7, 8}), v);
    }
    {
        SmallVector<uint16_t, 4> v = {3, 6, 5, 7, 8};
        v.erase(v.begin() + 2);
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 4>{3, 6, 7, 8}), v);
    }

        // Delete in extended part of small array
    {
        SmallVector<uint16_t, 1> v = {3, 6, 5};
        v.erase(v.begin());
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 1>{6, 5}), v);
    }
    {
        SmallVector<uint16_t, 1> v = {3, 6, 5};
        v.erase(v.begin() + 1);
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 1>{3, 5}), v);
    }
    {
        SmallVector<uint16_t, 1> v = {3, 6, 5};
        v.erase(v.begin() + 2);
        CPPUNIT_ASSERT_EQUAL((SmallVector<uint16_t, 1>{3, 6}), v);
    }
}

namespace {
    void foo(const SmallVector<uint16_t, 4>&) {
    }
}

void
SmallVectorTest::testCopy()
{
    foo(SmallVector<uint16_t, 4>{3, 2});
    SmallVector<uint16_t, 4> v{1, 2, 3};
    foo(v);
    foo({});
}

} // lib
} // storage
