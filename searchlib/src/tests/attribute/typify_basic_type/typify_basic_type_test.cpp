// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/typify_basic_type.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <string>
#include <type_traits>
#include <typeindex>
#include <typeinfo>

using search::attribute::BasicType;
using search::attribute::TypifyBasicType;
using vespalib::typify_invoke;
using vespalib::TypifyBool;
using vespalib::TypifyValue;

namespace {

using String = TypifyBasicType::String;
using Unsupported = TypifyBasicType::Unsupported;

struct GetType {
    template <typename T> static std::type_index invoke() { return std::type_index(typeid(T)); }
};

std::type_index resolved_type(BasicType::Type type) {
    return typify_invoke<1, TypifyBasicType, GetType>(type);
}

template <typename T> std::type_index type_of() {
    return std::type_index(typeid(T));
}

} // namespace

TEST(TypifyBasicTypeTest, numeric_types_resolve_to_value_types) {
    EXPECT_EQ(type_of<int8_t>(), resolved_type(BasicType::INT8));
    EXPECT_EQ(type_of<int16_t>(), resolved_type(BasicType::INT16));
    EXPECT_EQ(type_of<int32_t>(), resolved_type(BasicType::INT32));
    EXPECT_EQ(type_of<int64_t>(), resolved_type(BasicType::INT64));
    EXPECT_EQ(type_of<float>(), resolved_type(BasicType::FLOAT));
    EXPECT_EQ(type_of<double>(), resolved_type(BasicType::DOUBLE));
}

TEST(TypifyBasicTypeTest, string_type_resolves_to_string_tag) {
    EXPECT_EQ(type_of<String>(), resolved_type(BasicType::STRING));
}

TEST(TypifyBasicTypeTest, other_types_resolve_to_unsupported_tag) {
    for (auto type : {BasicType::NONE, BasicType::BOOL, BasicType::PREDICATE, BasicType::TENSOR, BasicType::REFERENCE,
                      BasicType::RAW, BasicType::MAX_TYPE})
    {
        SCOPED_TRACE(static_cast<int>(type)); // asString() is out of bounds for MAX_TYPE
        EXPECT_EQ(type_of<Unsupported>(), resolved_type(type));
    }
}

namespace {

template <typename T> const char* name_of() {
    if constexpr (std::is_same_v<T, int32_t>) {
        return "int32";
    } else if constexpr (std::is_same_v<T, String>) {
        return "string";
    } else {
        return "other";
    }
}

struct Describe {
    template <typename T, typename Flag> static std::string invoke(const std::string& prefix) {
        return prefix + name_of<T>() + (Flag::value ? "/true" : "/false");
    }
};

/*
 * BasicType::Type is an unscoped enum and converts implicitly to bool, so combining
 * TypifyBasicType with TypifyBool relies on overload resolution picking the exact match.
 */
std::string describe(BasicType::Type type, bool flag) {
    return typify_invoke<2, TypifyValue<TypifyBasicType, TypifyBool>, Describe>(type, flag, std::string("x:"));
}

} // namespace

TEST(TypifyBasicTypeTest, can_be_combined_with_typify_bool) {
    EXPECT_EQ("x:int32/true", describe(BasicType::INT32, true));
    EXPECT_EQ("x:int32/false", describe(BasicType::INT32, false));
    EXPECT_EQ("x:string/true", describe(BasicType::STRING, true));
    EXPECT_EQ("x:other/false", describe(BasicType::TENSOR, false));
}

GTEST_MAIN_RUN_ALL_TESTS()
