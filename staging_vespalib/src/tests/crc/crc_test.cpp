// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/crc.h>
#include <boost/crc.hpp>
#include <vector>

using namespace vespalib;

class Test : public TestApp
{
public:
    int Main() override;
    void testCorrectNess();
    void testBenchmark(bool our, size_t bufSz, size_t numRep);
};

int
Test::Main()
{
    TEST_INIT("crc_test");
    testCorrectNess();
    if (_argc >= 2) {
        testBenchmark(false, 1024, 1000*1000);
    } else {
        testBenchmark(true, 1024, 1000*1000);
    }
    TEST_DONE();
}

void Test::testCorrectNess()
{
    const char *a[7] = { "", "a", "ab", "abc", "abcd", "abcde", "doc:crawler:http://www.ntnu.no/" };
    for (size_t i(0); i < sizeof(a)/sizeof(a[0]); i++) {
        uint32_t vespaCrc32 = crc_32_type::crc(a[i], strlen(a[i]));
        boost::crc_32_type calculator;
        calculator.process_bytes(a[i], strlen(a[i]));
        EXPECT_EQUAL(vespaCrc32, calculator.checksum());
        vespalib::crc_32_type calculator2;
        calculator2.process_bytes(a[i], strlen(a[i]));
        EXPECT_EQUAL(vespaCrc32, calculator2.checksum());
        EXPECT_EQUAL(calculator.checksum(), calculator2.checksum());
    }
    vespalib::crc_32_type calculator2;
    boost::crc_32_type calculator;
    for (size_t i(0); i < sizeof(a)/sizeof(a[0]); i++) {
        calculator.process_bytes(a[i], strlen(a[i]));
        calculator2.process_bytes(a[i], strlen(a[i]));
        EXPECT_EQUAL(calculator.checksum(), calculator2.checksum());
    }
    EXPECT_EQUAL(calculator.checksum(), calculator2.checksum());
}

void Test::testBenchmark(bool our, size_t bufSz, size_t numRep)
{
    std::vector<char> a(numRep+bufSz);
    for(size_t i(0), m(a.size()); i < m; i++) {
        a[i] = i&0xff;
    }
    uint32_t sum(0);
    if (our) {
        for (size_t i(0); i < (numRep); i++) {
            //sum ^= crc_32_type::crc(&a[i], bufSz);
            vespalib::crc_32_type calculator;
            calculator.process_bytes(&a[i], bufSz);
            sum ^=calculator.checksum();
        }
    } else {
        for (size_t i(0); i < (numRep); i++) {
            boost::crc_32_type calculator;
            calculator.process_bytes(&a[i], bufSz);
            sum ^=calculator.checksum();
        }
    }
    printf("sum = %x\n", sum);
}

TEST_APPHOOK(Test)
