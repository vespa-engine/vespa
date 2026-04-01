// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/databuffer.h>
#include <vespa/fnet/frt/values.h>
#include <vespa/fnet/info.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stash.h>

using vespalib::Stash;

uint8_t  int8_arr[3] = {1, 2, 3};
uint16_t int16_arr[3] = {2, 4, 6};
uint32_t int32_arr[3] = {4, 8, 12};
uint64_t int64_arr[3] = {8, 16, 24};
float    float_arr[3] = {0.5, 1.0, 1.5};
double   double_arr[3] = {0.25, 0.50, 0.75};

template <typename T> void arr_cpy(T* dst, const T* src, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        dst[i] = src[i];
    }
}

void fillValues(FRT_Values& values) {
    {
        values.AddInt8(int8_arr[0]);
        arr_cpy(values.AddInt8Array(3), int8_arr, 3);
        values.AddInt8Array(int8_arr, 3);
        values.AddInt8ArrayRef(int8_arr, 3);
    }
    {
        values.AddInt16(int16_arr[0]);
        arr_cpy(values.AddInt16Array(3), int16_arr, 3);
        values.AddInt16Array(int16_arr, 3);
        values.AddInt16ArrayRef(int16_arr, 3);
    }
    {
        values.AddInt32(int32_arr[0]);
        arr_cpy(values.AddInt32Array(3), int32_arr, 3);
        values.AddInt32Array(int32_arr, 3);
        values.AddInt32ArrayRef(int32_arr, 3);
    }
    {
        values.AddInt64(int64_arr[0]);
        arr_cpy(values.AddInt64Array(3), int64_arr, 3);
        values.AddInt64Array(int64_arr, 3);
        values.AddInt64ArrayRef(int64_arr, 3);
    }
    {
        values.AddFloat(float_arr[0]);
        arr_cpy(values.AddFloatArray(3), float_arr, 3);
        values.AddFloatArray(float_arr, 3);
        values.AddFloatArrayRef(float_arr, 3);
    }
    {
        values.AddDouble(double_arr[0]);
        arr_cpy(values.AddDoubleArray(3), double_arr, 3);
        values.AddDoubleArray(double_arr, 3);
        values.AddDoubleArrayRef(double_arr, 3);
    }
    {
        values.AddInt32Array(0);
        values.AddInt32Array(nullptr, 0);
        values.AddInt32ArrayRef(nullptr, 0);
    }
    {
        values.AddString("foo");
        values.AddString("bar", 3);
        strcpy(values.AddString(3), "baz");
        values.AddString(nullptr, 0);
        uint32_t zero_len = 0;
        *values.AddString(zero_len) = '\0';
        FRT_StringValue* str_arr = values.AddStringArray(4);
        values.SetString(str_arr, "foo");
        values.SetString(str_arr + 1, "bar");
        values.SetString(str_arr + 2, "baz", 3);
        values.SetString(str_arr + 3, nullptr, 0);
    }
    {
        values.AddData("foo", 3);
        memcpy(values.AddData(3), "bar", 3);
        values.AddData(nullptr, 0);
        values.AddData(0);
        FRT_DataValue* data_arr = values.AddDataArray(4);
        values.SetData(data_arr, "foo", 3);
        values.SetData(data_arr + 1, "bar", 3);
        values.SetData(data_arr + 2, "baz", 3);
        values.SetData(data_arr + 3, nullptr, 0);
    }
}

void checkValues(FRT_Values& values) {
    ASSERT_EQ(38u, values.GetNumValues());
    ASSERT_EQ(std::string("bBBBhHHHiIIIlLLLfFFFdDDDIIIsssssSxxxxX"), values.GetTypeString());
    size_t idx = 0;
    EXPECT_EQ(int8_arr[0], values[idx++]._intval8);
    for (size_t i = 0; i < 3; ++i, ++idx) {
        ASSERT_EQ(3u, values[idx]._int8_array._len);
        for (size_t j = 0; j < 3; ++j) {
            EXPECT_EQ(int8_arr[j], values[idx]._int8_array._pt[j]);
        }
    }
    EXPECT_EQ(int16_arr[0], values[idx++]._intval16);
    for (size_t i = 0; i < 3; ++i, ++idx) {
        ASSERT_EQ(3u, values[idx]._int16_array._len);
        for (size_t j = 0; j < 3; ++j) {
            EXPECT_EQ(int16_arr[j], values[idx]._int16_array._pt[j]);
        }
    }
    EXPECT_EQ(int32_arr[0], values[idx++]._intval32);
    for (size_t i = 0; i < 3; ++i, ++idx) {
        ASSERT_EQ(3u, values[idx]._int32_array._len);
        for (size_t j = 0; j < 3; ++j) {
            EXPECT_EQ(int32_arr[j], values[idx]._int32_array._pt[j]);
        }
    }
    EXPECT_EQ(int64_arr[0], values[idx++]._intval64);
    for (size_t i = 0; i < 3; ++i, ++idx) {
        ASSERT_EQ(3u, values[idx]._int64_array._len);
        for (size_t j = 0; j < 3; ++j) {
            EXPECT_EQ(int64_arr[j], values[idx]._int64_array._pt[j]);
        }
    }
    EXPECT_EQ(float_arr[0], values[idx++]._float);
    for (size_t i = 0; i < 3; ++i, ++idx) {
        ASSERT_EQ(3u, values[idx]._float_array._len);
        for (size_t j = 0; j < 3; ++j) {
            EXPECT_EQ(float_arr[j], values[idx]._float_array._pt[j]);
        }
    }
    EXPECT_EQ(double_arr[0], values[idx++]._double);
    for (size_t i = 0; i < 3; ++i, ++idx) {
        ASSERT_EQ(3u, values[idx]._double_array._len);
        for (size_t j = 0; j < 3; ++j) {
            EXPECT_EQ(double_arr[j], values[idx]._double_array._pt[j]);
        }
    }
    // Empty int32 arrays
    for (size_t i = 0; i < 3; ++i, ++idx) {
        ASSERT_EQ(0u, values[idx]._int32_array._len);
    }
    EXPECT_EQ(std::string("foo"), std::string(values[idx]._string._str, values[idx]._string._len));
    ++idx;
    EXPECT_EQ(std::string("bar"), std::string(values[idx]._string._str, values[idx]._string._len));
    ++idx;
    EXPECT_EQ(std::string("baz"), std::string(values[idx]._string._str, values[idx]._string._len));
    ++idx;
    EXPECT_EQ(std::string(""), std::string(values[idx]._string._str, values[idx]._string._len));
    ++idx;
    EXPECT_EQ(std::string(""), std::string(values[idx]._string._str, values[idx]._string._len));
    ++idx;
    ASSERT_EQ(4u, values[idx]._string_array._len);
    EXPECT_EQ(std::string("foo"),
              std::string(values[idx]._string_array._pt[0]._str, values[idx]._string_array._pt[0]._len));
    EXPECT_EQ(std::string("bar"),
              std::string(values[idx]._string_array._pt[1]._str, values[idx]._string_array._pt[1]._len));
    EXPECT_EQ(std::string("baz"),
              std::string(values[idx]._string_array._pt[2]._str, values[idx]._string_array._pt[2]._len));
    EXPECT_EQ(std::string(""),
              std::string(values[idx]._string_array._pt[3]._str, values[idx]._string_array._pt[3]._len));
    ++idx;
    EXPECT_EQ(std::string("foo"), std::string(values[idx]._data._buf, values[idx]._data._len));
    ++idx;
    EXPECT_EQ(std::string("bar"), std::string(values[idx]._data._buf, values[idx]._data._len));
    ++idx;
    EXPECT_EQ(0, values[idx]._data._len);
    ++idx;
    EXPECT_EQ(0, values[idx]._data._len);
    ++idx;
    ASSERT_EQ(4u, values[idx]._data_array._len);
    EXPECT_EQ(std::string("foo"),
              std::string(values[idx]._data_array._pt[0]._buf, values[idx]._data_array._pt[0]._len));
    EXPECT_EQ(std::string("bar"),
              std::string(values[idx]._data_array._pt[1]._buf, values[idx]._data_array._pt[1]._len));
    EXPECT_EQ(std::string("baz"),
              std::string(values[idx]._data_array._pt[2]._buf, values[idx]._data_array._pt[2]._len));
    EXPECT_EQ(0, values[idx]._data_array._pt[3]._len);
    ++idx;
    EXPECT_EQ(38u, idx);
}

void checkValues(FRT_Values& v1, FRT_Values& v2) {
    checkValues(v1);
    checkValues(v2);
    EXPECT_TRUE(v1.Equals(&v2));
    EXPECT_TRUE(v2.Equals(&v1));
}

void check_empty_values(FRT_Values& values) { ASSERT_EQ(0, values.GetNumValues()); }

void check_empty_values(FRT_Values& v1, FRT_Values& v2) {
    {
        SCOPED_TRACE("v1");
        check_empty_values(v1);
    }
    {
        SCOPED_TRACE("v2");
        check_empty_values(v2);
    }
    EXPECT_TRUE(v1.Equals(&v2));
    EXPECT_TRUE(v2.Equals(&v1));
}

TEST(ValuesTest, set_and_get) {
    Stash      f1;
    FRT_Values f2(f1);
    fillValues(f2);
    checkValues(f2);
}

TEST(ValuesTest, set_and_get_empty) {
    Stash      f1;
    FRT_Values f2(f1);
    check_empty_values(f2);
}

TEST(ValuesTest, encode_and_decode_big_endian) {
    Stash           f1;
    FRT_Values      f2(f1);
    FNET_DataBuffer f3;
    FRT_Values      f4(f1);
    fillValues(f2);
    f2.EncodeBig(&f3);
    EXPECT_EQ(f2.GetLength(), f3.GetDataLen());
    EXPECT_TRUE(f4.DecodeBig(&f3, f3.GetDataLen()));
    checkValues(f2, f4);
}

TEST(ValuesTest, encode_and_decode_big_endian_empty) {
    Stash           f1;
    FRT_Values      f2(f1);
    FNET_DataBuffer f3;
    FRT_Values      f4(f1);
    f2.EncodeBig(&f3);
    EXPECT_EQ(f2.GetLength(), f3.GetDataLen());
    EXPECT_TRUE(f4.DecodeBig(&f3, f3.GetDataLen()));
    check_empty_values(f2, f4);
}

TEST(ValuesTest, encode_and_decode_host_endian) {
    Stash           f1;
    FRT_Values      f2(f1);
    FNET_DataBuffer f3;
    FRT_Values      f4(f1);
    fillValues(f2);
    f2.EncodeCopy(&f3);
    EXPECT_EQ(f2.GetLength(), f3.GetDataLen());
    EXPECT_TRUE(f4.DecodeCopy(&f3, f3.GetDataLen()));
    checkValues(f2, f4);
}

TEST(ValuesTest, encode_and_decode_host_endian_empty) {
    Stash           f1;
    FRT_Values      f2(f1);
    FNET_DataBuffer f3;
    FRT_Values      f4(f1);
    f2.EncodeCopy(&f3);
    EXPECT_EQ(f2.GetLength(), f3.GetDataLen());
    EXPECT_TRUE(f4.DecodeCopy(&f3, f3.GetDataLen()));
    check_empty_values(f2, f4);
}

TEST(ValuesTest, decode_little_if_host_is_little) {
    Stash           f1;
    FRT_Values      f2(f1);
    FNET_DataBuffer f3;
    FRT_Values      f4(f1);
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_LITTLE) {
        fillValues(f2);
        f2.EncodeCopy(&f3);
        EXPECT_EQ(f2.GetLength(), f3.GetDataLen());
        EXPECT_TRUE(f4.DecodeLittle(&f3, f3.GetDataLen()));
        checkValues(f2, f4);
    } else {
        GTEST_SKIP() << "host is not little endian, coverage will suffer...";
    }
}

TEST(ValuesTest, decode_little_if_host_is_little_empty) {
    Stash           f1;
    FRT_Values      f2(f1);
    FNET_DataBuffer f3;
    FRT_Values      f4(f1);
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_LITTLE) {
        f2.EncodeCopy(&f3);
        EXPECT_EQ(f2.GetLength(), f3.GetDataLen());
        EXPECT_TRUE(f4.DecodeLittle(&f3, f3.GetDataLen()));
        check_empty_values(f2, f4);
    } else {
        GTEST_SKIP() << "host is not little endian, coverage will suffer...";
    }
}

TEST(ValuesTest, print_values) {
    Stash      f1;
    FRT_Values f2(f1);
    fillValues(f2);
    f2.Print();
}

TEST(ValuesTest, print_values_empty) {
    Stash      f1;
    FRT_Values f2(f1);
    f2.Print();
}

GTEST_MAIN_RUN_ALL_TESTS()
