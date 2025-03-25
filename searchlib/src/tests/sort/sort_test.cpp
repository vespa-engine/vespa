// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/searchlib/common/converters.h>
#include <vespa/searchlib/uca/ucaconverter.h>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/vespalib/testkit/test_path.h>
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
using search::common::sortspec::MissingPolicy;
using search::common::sortspec::SortOrder;

namespace search::common::sortspec {

void PrintTo(SortOrder sort_order, std::ostream *os) {
    switch (sort_order) {
        case SortOrder::ASCENDING:
            *os << "ASCENDING";
            break;
        case SortOrder::DESCENDING:
            *os << "DESCENDING";
            break;
    }
}

void PrintTo(MissingPolicy missing_policy, std::ostream *os) {
    switch (missing_policy) {
        case MissingPolicy::DEFAULT:
            *os << "DEFAULT";
            break;
        case MissingPolicy::FIRST:
            *os << "FIRST";
            break;
        case MissingPolicy::LAST:
            *os << "LAST";
            break;
        case MissingPolicy::AS:
            *os << "AS";
            break;
    }
}

}

struct LoadedStrings
{
    explicit LoadedStrings(const char * v=nullptr) : _value(v), _currRadix(_value) { }

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

    class ValueCompare {
    public:
        bool operator() (const LoadedStrings & x, const LoadedStrings & y) const {
            return strcmp(x._value, y._value) < 0;
        }
    };
    const char * _value;
    const char * _currRadix;
};

TEST(SortTest, testIcu)
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

TEST(SortTest, testUnsignedIntegerSort)
{
    search::NumericRadixSorter<uint32_t, true> S;
    S(nullptr, 0);

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
void testSignedIntegerSort()
{
    search::NumericRadixSorter<T, true> S;
    S(nullptr, 0);

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

TEST(SortTest, testSignedIntegerSort) {
    testSignedIntegerSort<int32_t>();
    testSignedIntegerSort<int64_t>();
}

TEST(SortTest, testStringSort)
{
    Array<LoadedStrings> array1(1);

    unsigned int N(0x1000);
    Array<LoadedStrings> loaded(N);
    std::vector<uint32_t> radixScratchPad(N);
    search::radix_sort(LoadedStrings::ValueRadix(), LoadedStrings::ValueCompare(), search::AlwaysEof<LoadedStrings>(), 1, static_cast<LoadedStrings *>(nullptr), 0, &radixScratchPad[0], 0);

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
        ASSERT_FALSE(vc(loaded[i], loaded[i-1]));
    }
}

TEST(SortTest, testStringCaseInsensitiveSort)
{
}

TEST(SortTest, testSortSpec)
{
    UcaConverterFactory ucaFactory;
    {
        SortSpec sortspec("-name", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE( sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_FALSE(sortspec[0]._converter);
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }

    {
        SortSpec sortspec("-lowercase(name)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<LowercaseConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }

    {
        SortSpec sortspec("-uca(name,nn_no)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,PRIMARY)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,SECONDARY)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,TERTIARY)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,QUATERNARY)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("-uca(name,nn_no,IDENTICAL)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("-uca(name,zh)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("-uca(name,finnes_ikke)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<UcaConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::DEFAULT, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
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

TEST(SortTest, sortspec_missing)
{
    UcaConverterFactory ucaFactory;
    {
        SortSpec sortspec("+missing(name,first)", ucaFactory);
        EXPECT_EQ(1u, sortspec.size());
        EXPECT_EQ("name", sortspec[0]._field);
        EXPECT_TRUE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::ASCENDING, sortspec[0]._sort_order);
        EXPECT_FALSE(sortspec[0]._converter);
        EXPECT_EQ(MissingPolicy::FIRST, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("+missing(name,last)", ucaFactory);
        EXPECT_EQ(1u, sortspec.size());
        EXPECT_EQ("name", sortspec[0]._field);
        EXPECT_TRUE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::ASCENDING, sortspec[0]._sort_order);
        EXPECT_FALSE(sortspec[0]._converter);
        EXPECT_EQ(MissingPolicy::LAST, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("+missing(name,as,default)", ucaFactory);
        EXPECT_EQ(1u, sortspec.size());
        EXPECT_EQ("name", sortspec[0]._field);
        EXPECT_TRUE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::ASCENDING, sortspec[0]._sort_order);
        EXPECT_FALSE(sortspec[0]._converter);
        EXPECT_EQ(MissingPolicy::AS, sortspec[0]._missing_policy);
        EXPECT_EQ("default", sortspec[0]._missing_value);
    }
    {
        SortSpec sortspec("+missing(name,as,\"quoted \\\\ \\\" default\")", ucaFactory);
        EXPECT_EQ(1u, sortspec.size());
        EXPECT_EQ("name", sortspec[0]._field);
        EXPECT_TRUE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::ASCENDING, sortspec[0]._sort_order);
        EXPECT_FALSE(sortspec[0]._converter);
        EXPECT_EQ(MissingPolicy::AS, sortspec[0]._missing_policy);
        EXPECT_EQ("quoted \\ \" default", sortspec[0]._missing_value);
    }
    {
        VESPA_EXPECT_EXCEPTION(SortSpec sortSpec("-missing(name,as,\"default", ucaFactory),
            std::runtime_error,
            "Expected '\"', end of spec reached at [-missing(name,as,\"default][]");
    }
    {
        VESPA_EXPECT_EXCEPTION(SortSpec sortSpec("-missing(name,as,\"bad quoting \\n here\"", ucaFactory),
            std::runtime_error,
            "Expected '\\' or '\"', got 'n' at [-missing(name,as,\"bad quoting \\][n here\"]");
    }
    {
        SortSpec sortspec("-missing(lowercase(name),last)", ucaFactory);
        EXPECT_EQ(sortspec.size(), 1u);
        EXPECT_EQ(sortspec[0]._field, "name");
        EXPECT_FALSE(sortspec[0]._ascending);
        EXPECT_EQ(SortOrder::DESCENDING, sortspec[0]._sort_order);
        EXPECT_TRUE(sortspec[0]._converter);
        EXPECT_NE(nullptr, dynamic_cast<LowercaseConverter *>(sortspec[0]._converter.get()));
        EXPECT_EQ(MissingPolicy::LAST, sortspec[0]._missing_policy);
        EXPECT_EQ("", sortspec[0]._missing_value);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
