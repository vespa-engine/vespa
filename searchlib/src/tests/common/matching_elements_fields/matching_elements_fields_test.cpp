// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/common/matching_elements_fields.h>

using namespace search;

namespace {

std::string str(const std::string &s) { return std::string(s.data(), s.size()); }

}

struct MatchingElementsFieldsTest : ::testing::Test {
    MatchingElementsFields fields;
    MatchingElementsFieldsTest() : fields() {
        fields.add_mapping("foo", "foo.a");
        fields.add_mapping("foo", "foo.b");
        fields.add_mapping("bar", "bar.x");
        fields.add_mapping("bar", "bar.y");
        fields.add_field("baz");
    }
    ~MatchingElementsFieldsTest() = default;
};

TEST_F(MatchingElementsFieldsTest, require_that_field_can_be_identified) {
    EXPECT_TRUE(fields.has_field("foo"));
    EXPECT_TRUE(fields.has_field("bar"));
    EXPECT_TRUE(fields.has_field("baz"));
    EXPECT_TRUE(fields.has_field("foo.a"));
    EXPECT_TRUE(fields.has_field("foo.b"));
    EXPECT_TRUE(!fields.has_field("foo.c"));
    EXPECT_TRUE(fields.has_field("bar.x"));
    EXPECT_TRUE(fields.has_field("bar.y"));
    EXPECT_TRUE(!fields.has_field("bar.z"));
    EXPECT_TRUE(!fields.has_field("bogus"));
}

TEST_F(MatchingElementsFieldsTest, require_that_struct_field_maps_to_enclosing_field_name) {
    EXPECT_EQ(str(fields.enclosing_field("foo.a")), str("foo"));
    EXPECT_EQ(str(fields.enclosing_field("foo.b")), str("foo"));
    EXPECT_EQ(str(fields.enclosing_field("bar.x")), str("bar"));
    EXPECT_EQ(str(fields.enclosing_field("bar.y")), str("bar"));
}

TEST_F(MatchingElementsFieldsTest, require_that_nonexisting_field_maps_to_itself) {
    EXPECT_EQ(str(fields.enclosing_field("foo")), str("foo"));
    EXPECT_EQ(str(fields.enclosing_field("bar")), str("bar"));
    EXPECT_EQ(str(fields.enclosing_field("bogus")), str("bogus"));
}

GTEST_MAIN_RUN_ALL_TESTS()
