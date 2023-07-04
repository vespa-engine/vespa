// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/numeric_sort_blob_writer.h>
#include <vespa/searchlib/attribute/string_sort_blob_writer.h>
#include <vespa/searchlib/common/converters.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/sort.h>

using search::attribute::NumericSortBlobWriter;
using search::attribute::SortBlobWriter;
using search::attribute::StringSortBlobWriter;
using search::common::BlobConverter;
using search::common::LowercaseConverter;
using vespalib::ConstArrayRef;

namespace {

using SortData = std::vector<unsigned char>;

SortData missing_value{1};

template <typename T, bool asc>
SortData
serialized_present_numeric(T value)
{
    SortData s;
    s.resize(1 + sizeof(T));
    s[0] = SortBlobWriter::has_value;
    auto ret = vespalib::serializeForSort<vespalib::convertForSort<T, asc>>(value, s.data() + 1, s.size() - 1);
    assert(size_t(ret) == s.size() - 1);
    return s;
}

SortData
serialized_present_string(const char *value, bool asc)
{
    ConstArrayRef<unsigned char> src(reinterpret_cast<const unsigned char*>(value), strlen(value) + 1);
    SortData s;
    s.reserve(src.size() + 1);
    s.emplace_back(SortBlobWriter::has_value);
    unsigned char xor_value = asc ? 0 : 255;
    for (auto c : src) {
        s.emplace_back(c ^ xor_value);
    }
    return s;
}

template <typename T>
SortData
serialized_present(T value, bool asc)
{
    if constexpr (std::is_same_v<T, const char*>) {
        return serialized_present_string(value, asc);
    } else {
        if (asc) {
            return serialized_present_numeric<T, true>(value);
        }
        return serialized_present_numeric<T, false>(value);
    }
}

template <typename T, bool asc>
SortData
sort_data_numeric(std::vector<T> values)
{
    size_t len = 0;
    SortData s;
    while (true) {
        s.clear();
        s.resize(len);
        NumericSortBlobWriter<T, asc> writer;
        for (auto& v : values) {
            writer.candidate(v);
        }
        auto result = writer.write(s.data(), s.size());
        if (result >= 0) {
            s.resize(result);
            return s;
        }
        ++len;
    }
}

SortData
sort_data_string(std::vector<const char*> values, const BlobConverter* bc, bool asc)
{
    size_t len = 0;
    SortData s;
    while (true) {
        s.clear();
        s.resize(len);
        StringSortBlobWriter writer(s.data(), s.size(), bc, asc);
        bool fail = false;
        for (auto& v : values) {
            if (!writer.candidate(v)) {
                fail = true;
                break;
            }
        }
        if (!fail) {
            auto result = writer.write();
            if (result >= 0) {
                s.resize(result);
                return s;
            }
        }
        ++len;
    }
}

SortData
sort_data_string(std::vector<const char*> values, bool asc)
{
    return sort_data_string(values, nullptr, asc);
}

template <typename T>
SortData
sort_data(std::vector<T> values, bool asc)
{
    if constexpr (std::is_same_v<T, const char*>) {
        return sort_data_string(values, asc);
    }
    if (asc) {
        return sort_data_numeric<T, true>(std::move(values));
    }
    return sort_data_numeric<T, false>(std::move(values));
}

SortData
switch_sort_order(SortData value)
{
    assert(value.size() >= 1);
    ConstArrayRef<unsigned char> src(value.data() + 1, value.size() - 1);
    SortData s;
    s.reserve(src.size() + 1);
    s.emplace_back(value[0]);
    for (auto c : src) {
        s.emplace_back(c ^ 255);
    }
    return s;
}

struct SetupHook {
    SetupHook();
};

SetupHook::SetupHook()
{
    Fast_NormalizeWordFolder::Setup(Fast_NormalizeWordFolder::DO_ACCENT_REMOVAL |
                                    Fast_NormalizeWordFolder::DO_SHARP_S_SUBSTITUTION |
                                    Fast_NormalizeWordFolder::DO_LIGATURE_SUBSTITUTION |
                                    Fast_NormalizeWordFolder::DO_MULTICHAR_EXPANSION);
}

SetupHook setup_hook;

}

template <typename Param>
class SortBlobWritersTest : public ::testing::Test
{
protected:
    SortBlobWritersTest();
    ~SortBlobWritersTest() override;
};


template <typename Param>
SortBlobWritersTest<Param>::SortBlobWritersTest()
    : ::testing::Test()
{
}

template <typename Param>
SortBlobWritersTest<Param>::~SortBlobWritersTest() = default;

struct Int8Params {
    using Type = int8_t;
    static constexpr int8_t value = 42;
    static SortData sort_asc;
    static SortData sort_desc;
    static std::vector<int8_t> values;
    static constexpr int8_t min_value = -4;
    static constexpr int8_t max_value = 9;
};

SortData Int8Params::sort_asc{0, 128 ^ 42};
SortData Int8Params::sort_desc{0, 127 ^ 42};
std::vector<int8_t> Int8Params::values{5, 7, -4, 9};

struct Int16Params {
    using Type = int16_t;
    static constexpr int16_t value = 43;
    static SortData sort_asc;
    static SortData sort_desc;
    static std::vector<int16_t> values;
    static constexpr int16_t min_value = -4;
    static constexpr int16_t max_value = 9;
};

SortData Int16Params::sort_asc{0, 128, 43};
SortData Int16Params::sort_desc{0, 127, 255 ^ 43};
std::vector<int16_t> Int16Params::values{5, 7, -4, 9};

struct Int32Params {
    using Type = int32_t;
    static constexpr int32_t value = 44;
    static SortData sort_asc;
    static SortData sort_desc;
    static std::vector<int32_t> values;
    static constexpr int32_t min_value = -4;
    static constexpr int32_t max_value = 9;
};

SortData Int32Params::sort_asc{0, 128, 0, 0, 44};
SortData Int32Params::sort_desc{0, 127, 255, 255, 255 ^ 44};
std::vector<int32_t> Int32Params::values{5, 7, -4, 9};

struct Int64Params {
    using Type = int64_t;
    static constexpr int64_t value = 45;
    static SortData sort_asc;
    static SortData sort_desc;
    static std::vector<int64_t> values;
    static constexpr int64_t min_value = -4;
    static constexpr int64_t max_value = 9;
};

SortData Int64Params::sort_asc{0, 128, 0, 0, 0, 0, 0, 0, 45};
SortData Int64Params::sort_desc{0, 127, 255, 255, 255, 255, 255, 255, 255 ^ 45};
std::vector<int64_t> Int64Params::values{5, 7, -4, 9};

struct FloatParams {
    using Type = float;
    static constexpr float value = 46;
    static std::vector<float> values;
    static std::vector<float> values_with_nan;
    static std::vector<float> values_only_nan;
    static constexpr float min_value = -4;
    static constexpr float max_value = 9;
};

std::vector<float> FloatParams::values{5, 7, -4, 9};
std::vector<float> FloatParams::values_with_nan{5, 7, std::numeric_limits<float>::quiet_NaN(), -4, 9};
std::vector<float> FloatParams::values_only_nan{std::numeric_limits<float>::quiet_NaN()};

struct DoubleParams {
    using Type = double;
    static constexpr double value = 47;
    static std::vector<double> values;
    static std::vector<double> values_with_nan;
    static std::vector<double> values_only_nan;
    static constexpr double min_value = -4;
    static constexpr double max_value = 9;
};

std::vector<double> DoubleParams::values{5, 7, -4, 9};
std::vector<double> DoubleParams::values_with_nan{5, 7, std::numeric_limits<double>::quiet_NaN(), -4, 9};
std::vector<double> DoubleParams::values_only_nan{std::numeric_limits<double>::quiet_NaN()};

struct StringParams {
    using Type = const char*;
    static constexpr const char* value = "Hello";
    static std::vector<const char*> values;
    static constexpr const char* min_value = "always";
    static constexpr const char* max_value = "this";
};

std::vector<const char*> StringParams::values{"this", "always", "happens"};

using SortBlobWritersTestTypes = testing::Types<Int8Params, Int16Params, Int32Params, Int64Params, FloatParams, DoubleParams, StringParams>;

TYPED_TEST_SUITE(SortBlobWritersTest, SortBlobWritersTestTypes);

TYPED_TEST(SortBlobWritersTest, empty_arrays)
{
    using Type = typename TypeParam::Type;
    EXPECT_EQ(missing_value, sort_data<Type>({}, true));
    EXPECT_EQ(missing_value, sort_data<Type>({}, false));
}

TYPED_TEST(SortBlobWritersTest, single_values)
{
    using Type = typename TypeParam::Type;
    auto& value = TypeParam::value;
    EXPECT_EQ(serialized_present<Type>(value, true), sort_data<Type>({value}, true));
    EXPECT_EQ(serialized_present<Type>(value, false), sort_data<Type>({value}, false));
    if constexpr (std::is_integral_v<Type>) {
       EXPECT_EQ(TypeParam::sort_asc, sort_data<Type>({value}, true));
       EXPECT_EQ(TypeParam::sort_desc, sort_data<Type>({value}, false));
    }
    EXPECT_EQ(switch_sort_order(sort_data<Type>({value}, false)), sort_data<Type>({value}, true));
    EXPECT_EQ(switch_sort_order(sort_data<Type>({value}, true)), sort_data<Type>({value}, false));
    EXPECT_GT(missing_value, sort_data<Type>({value}, true));
    EXPECT_GT(missing_value, sort_data<Type>({value}, false));
}

TYPED_TEST(SortBlobWritersTest, multiple_values)
{
    using Type = typename TypeParam::Type;
    auto& values = TypeParam::values;
    EXPECT_EQ(serialized_present<Type>(TypeParam::min_value, true), sort_data<Type>(values, true));
    EXPECT_EQ(serialized_present<Type>(TypeParam::max_value, false), sort_data<Type>(values, false));
}

template <typename Param>
using SortBlobFloatingPointWritersTest = SortBlobWritersTest<Param>;

using SortBlobFloatingPointWritersTestTypes = testing::Types<FloatParams, DoubleParams>;

TYPED_TEST_SUITE(SortBlobFloatingPointWritersTest, SortBlobFloatingPointWritersTestTypes);

TYPED_TEST(SortBlobFloatingPointWritersTest, skip_nan_values)
{
    using Type = typename TypeParam::Type;
    auto& values_only_nan = TypeParam::values_only_nan;
    auto& values_with_nan = TypeParam::values_with_nan;
    EXPECT_EQ(missing_value, sort_data<Type>(values_only_nan, true));
    EXPECT_EQ(missing_value, sort_data<Type>(values_only_nan, false));
    EXPECT_EQ(serialized_present<Type>(TypeParam::min_value, true), sort_data<Type>(values_with_nan, true));
    EXPECT_EQ(serialized_present<Type>(TypeParam::max_value, false), sort_data<Type>(values_with_nan, false));
}

using SortBlobStringWriterTest = SortBlobWritersTest<const char*>;

TEST_F(SortBlobStringWriterTest, blob_converter_is_used)
{
    LowercaseConverter lowercase;
    EXPECT_EQ(serialized_present_string("hello", true), sort_data_string({"Hello"}, &lowercase, true));
    EXPECT_EQ(serialized_present_string("hello", false), sort_data_string({"Hello"}, &lowercase, false));
    EXPECT_EQ(serialized_present_string("always", true), sort_data_string({"Hello", "always"}, &lowercase, true));
    EXPECT_EQ(serialized_present_string("hello", false), sort_data_string({"Hello", "always"}, &lowercase, false));
}

TEST_F(SortBlobStringWriterTest, prefix_is_first)
{
    EXPECT_EQ(serialized_present_string("aaa", true), sort_data_string({"aaa", "aaaa"}, true));
    EXPECT_EQ(serialized_present_string("aaaa", false), sort_data_string({"aaa", "aaaa"}, false));
}

GTEST_MAIN_RUN_ALL_TESTS()
