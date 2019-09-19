// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/common/struct_field_mapper.h>

using namespace search;

namespace {

std::string str(const vespalib::string &s) { return std::string(s.data(), s.size()); }

}

struct StructFieldMapperTest : ::testing::Test {
    StructFieldMapper mapper;
    StructFieldMapperTest() : mapper() {
        mapper.add_mapping("foo", "foo.a");
        mapper.add_mapping("foo", "foo.b");
        mapper.add_mapping("bar", "bar.x");
        mapper.add_mapping("bar", "bar.y");
    }
    ~StructFieldMapperTest() = default;
};

TEST_F(StructFieldMapperTest, require_that_struct_field_can_be_identified) {
    EXPECT_TRUE(mapper.is_struct_field("foo"));
    EXPECT_TRUE(mapper.is_struct_field("bar"));
    EXPECT_TRUE(!mapper.is_struct_field("foo.a"));
    EXPECT_TRUE(!mapper.is_struct_field("bar.x"));
    EXPECT_TRUE(!mapper.is_struct_field("bogus"));
}

TEST_F(StructFieldMapperTest, require_that_struct_subfield_can_be_identified) {
    EXPECT_TRUE(!mapper.is_struct_subfield("foo"));
    EXPECT_TRUE(!mapper.is_struct_subfield("bar"));
    EXPECT_TRUE(mapper.is_struct_subfield("foo.a"));
    EXPECT_TRUE(mapper.is_struct_subfield("bar.x"));
    EXPECT_TRUE(!mapper.is_struct_subfield("bogus"));
}

TEST_F(StructFieldMapperTest, require_that_struct_subfield_maps_to_enclosing_struct_field_name) {
    EXPECT_EQ(str(mapper.get_struct_field("foo.a")), str("foo"));
    EXPECT_EQ(str(mapper.get_struct_field("foo.b")), str("foo"));
    EXPECT_EQ(str(mapper.get_struct_field("bar.x")), str("bar"));
    EXPECT_EQ(str(mapper.get_struct_field("bar.y")), str("bar"));
}

TEST_F(StructFieldMapperTest, require_that_nonexisting_struct_subfield_maps_to_empty_string) {
    EXPECT_EQ(str(mapper.get_struct_field("bogus")), str(""));
}

GTEST_MAIN_RUN_ALL_TESTS()
