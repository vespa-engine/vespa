// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/searchlib/common/converters.h>
#include <vespa/searchlib/uca/ucaconverter.h>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/objects/hexdump.h>
#include <vector>
#include <fstream>
#include <iostream>
#include <iomanip>
#include <stdexcept>
#include <unicode/ustring.h>

using vespalib::Array;
using namespace search::common;
using namespace search::uca;
using vespalib::ConstBufferRef;

class Test : public vespalib::TestApp
{
public:
    int Main() override;
    void testUnsignedIntegerSort();
    template <typename T>
    void testSignedIntegerSort();
    void testStringSort();
    void testIcu();
    void testStringCaseInsensitiveSort();
    void testSortSpec();
    void testSameAsJavaOrder();
};

struct LoadedStrings
{
    LoadedStrings(const char * v=NULL) : _value(v), _currRadix(_value) { }

    class ValueRadix
    {
    public:
        char operator () (LoadedStrings & x) const {
            unsigned char c(*x._currRadix);
            if (c) {
                x._currRadix++;
            }
            return c;
        }
    };

    class ValueCompare : public std::binary_function<LoadedStrings, LoadedStrings, bool> {
    public:
        bool operator() (const LoadedStrings & x, const LoadedStrings & y) const {
            return strcmp(x._value, y._value) < 0;
        }
    };
    const char * _value;
    const char * _currRadix;
};

void Test::testIcu()
{
    {
        const std::string src("Creation of Bob2007 this is atumated string\this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string;this is atumated string; _ 12345567890-=,./;'[;");
        std::vector<UChar> u16Buffer(100);
        UErrorCode status = U_ZERO_ERROR;
        int32_t u16Wanted(0);
        u_strFromUTF8(&u16Buffer[0], u16Buffer.size(), &u16Wanted, src.c_str(), -1, &status);
        ASSERT_TRUE(U_SUCCESS(status) || (status == U_INVALID_CHAR_FOUND) || ((status == U_BUFFER_OVERFLOW_ERROR) && (u16Wanted > (int)u16Buffer.size())));
    }
}

void Test::testUnsignedIntegerSort()
{
    search::NumericRadixSorter<uint32_t, true> S;
    S(NULL, 0);

    Array<uint32_t> array1(1);
    array1[0] = 1567;
    S(&array1[0], 1);
    ASSERT_TRUE(array1[0] == 1567);

    unsigned int N(0x100000);
    Array<uint32_t> array(N);
    unsigned seed(1);
    for(size_t i(0); i < N; i++) {
        array[i] = rand_r(&seed);
    }
    S(&array[0], N);
    for (size_t i(1); i < N; i++) {
        ASSERT_TRUE(array[i] >= array[i-1]);
    }
}

template<typename T>
class IntOrder {
public:
    uint64_t operator () (T v) const { return v ^ (std::numeric_limits<T>::max() + 1); }
};

template <typename T>
void Test::testSignedIntegerSort()
{
    search::NumericRadixSorter<T, true> S;
    S(NULL, 0);

    Array<T> array1(1);
    array1[0] = 1567;
    S(&array1[0], 1);
    ASSERT_TRUE(array1[0] == 1567);

    unsigned int N(0x100000);
    Array<T> array(N);
    unsigned seed(1);
    for(size_t i(0); i < N; i++) {
        T v = rand_r(&seed);
        array[i] = (i%2) ? v : -v;
    }
    S(&array[0], N);
    for (size_t i(1); i < N; i++) {
        ASSERT_TRUE(array[i] >= array[i-1]);
    }
}

void Test::testStringSort()
{
    Array<LoadedStrings> array1(1);

    unsigned int N(0x1000);
    Array<LoadedStrings> loaded(N);
    std::vector<uint32_t> radixScratchPad(N);
    search::radix_sort(LoadedStrings::ValueRadix(), LoadedStrings::ValueCompare(), search::AlwaysEof<LoadedStrings>(), 1, static_cast<LoadedStrings *>(NULL), 0, &radixScratchPad[0], 0);

    array1[0] = LoadedStrings("a");
    search::radix_sort(LoadedStrings::ValueRadix(), LoadedStrings::ValueCompare(), search::AlwaysEof<LoadedStrings>(), 1, &array1[0], 1, &radixScratchPad[0], 0);
    ASSERT_TRUE(strcmp(array1[0]._value, "a") == 0);

    loaded[0] = LoadedStrings("a");
    for(size_t i(1); i < N; i++) {
        loaded[i] = LoadedStrings("");
    }

    search::radix_sort(LoadedStrings::ValueRadix(), LoadedStrings::ValueCompare(), search::AlwaysEof<LoadedStrings>(), 1, &loaded[0], N, &radixScratchPad[0], 0);
    LoadedStrings::ValueCompare vc;
    for(size_t i(1); i < N; i++) {
        ASSERT_TRUE( ! vc(loaded[i], loaded[i-1]));
    }
}

void Test::testStringCaseInsensitiveSort()
{
}

void Test::testSortSpec()
{
    UcaConverterFactory ucaFactory;
    {
        SortSpec sortspec("-name", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() == NULL);
    }

    {
        SortSpec sortspec("-lowercase(name)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<LowercaseConverter *>(sortspec[0]._converter.get()) != NULL);
    }

    {
        SortSpec sortspec("-uca(name,nn_no)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()) != NULL);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,PRIMARY)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()) != NULL);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,SECONDARY)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()) != NULL);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,TERTIARY)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()) != NULL);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,QUATERNARY)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()) != NULL);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,IDENTICAL)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()) != NULL);
    }
    {
        SortSpec sortspec("-uca(name,zh)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()) != NULL);
    }
    {
        SortSpec sortspec("-uca(name,finnes_ikke)", ucaFactory);
        EXPECT_EQUAL(sortspec.size(), 1u);
        EXPECT_EQUAL(sortspec[0]._field, "name");
        EXPECT_TRUE( ! sortspec[0]._ascending);
        EXPECT_TRUE(sortspec[0]._converter.get() != NULL);
        EXPECT_TRUE(dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()) != NULL);
    }
    {
        try {
            SortSpec sortspec("-uca(name,nn_no,NTERTIARY)", ucaFactory);
            EXPECT_TRUE(false);
        } catch (const std::runtime_error & e) {
            EXPECT_TRUE(true);
            EXPECT_TRUE(strcmp(e.what(), "Illegal uca collation strength : NTERTIARY") == 0);
        }
    }
}

void Test::testSameAsJavaOrder()
{
    std::vector<vespalib::string> javaOrder;
    std::ifstream is("javaorder.zh");
    while (!is.eof()) {
        std::string line;
        getline(is, line);
        if (!is.eof()) {
            javaOrder.push_back(line);
        }
    }
    EXPECT_EQUAL(158u, javaOrder.size());
    UcaConverter uca("zh", "PRIMARY");
    vespalib::ConstBufferRef fkey = uca.convert(vespalib::ConstBufferRef(javaOrder[0].c_str(), javaOrder[0].size()));
    vespalib::string prev(fkey.c_str(), fkey.size());
    for (size_t i(1); i < javaOrder.size(); i++) {
        vespalib::ConstBufferRef key = uca.convert(vespalib::ConstBufferRef(javaOrder[i].c_str(), javaOrder[i].size()));
        vespalib::HexDump dump(key.c_str(), key.size());
        vespalib::string current(key.c_str(), key.size());
        UErrorCode status(U_ZERO_ERROR);
        UCollationResult cr = uca.getCollator().compareUTF8(javaOrder[i-1].c_str(), javaOrder[i].c_str(), status);
        std::cout << std::setw(3) << i << ": " << status << "(" << u_errorName(status) << ") - " << cr << " '" << dump << "'  : '" << javaOrder[i] << "'" << std::endl;
        EXPECT_TRUE(prev <= current);
        EXPECT_TRUE(U_SUCCESS(status));
        EXPECT_TRUE(cr == UCOL_LESS || cr == UCOL_EQUAL);
        prev = current;
    }
}


TEST_APPHOOK(Test);

int Test::Main()
{
    TEST_INIT("sort_test");

    testUnsignedIntegerSort();
    testSignedIntegerSort<int32_t>();
    testSignedIntegerSort<int64_t>();
    testStringSort();
    testStringCaseInsensitiveSort();
    testSortSpec();
    testIcu();
    testSameAsJavaOrder();

    TEST_DONE();
}
