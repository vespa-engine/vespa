// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/string_escape.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace ::testing;

TEST(StringEscapeTest, xml_attribute_special_chars_are_escaped) {
    // We always escape both " and ' since we don't know the quoting context of the enclosing attribute.
    EXPECT_EQ(xml_attribute_escaped("<>&\"'"), "&lt;&gt;&amp;&quot;&#39;");
}

TEST(StringEscapeTest, xml_attribute_regular_chars_are_not_escaped) {
    // Far from exhaustive, but should catch obvious mess-ups.
    EXPECT_EQ(xml_attribute_escaped("09azAZ.,()[]$!"), "09azAZ.,()[]$!");
}

TEST(StringEscapeTest, control_characters_are_escaped_in_attributes) {
    EXPECT_EQ(xml_attribute_escaped("\n"), "&#10;");
    EXPECT_EQ(xml_attribute_escaped("\r"), "&#13;");
    EXPECT_EQ(xml_attribute_escaped(stringref("\x00", 1)), "&#0;"); // Can't just invoke strlen with null byte :)
    EXPECT_EQ(xml_attribute_escaped("\x1f"), "&#31;");
}

TEST(StringEscapeTest, xml_content_special_chars_are_escaped) {
    EXPECT_EQ(xml_content_escaped("<>&"), "&lt;&gt;&amp;");
}

TEST(StringEscapeTest, xml_content_regular_chars_are_not_escaped) {
    EXPECT_EQ(xml_content_escaped("09azAZ.,()[]$!"), "09azAZ.,()[]$!");
    // Newlines are not escaped in content
    EXPECT_EQ(xml_content_escaped("\n"), "\n");
    // Quotes are not escaped in content
    EXPECT_EQ(xml_content_escaped("\"'"), "\"'");
}

TEST(StringEscapeTest, control_characters_are_escaped_in_content) {
    EXPECT_EQ(xml_content_escaped("\r"), "&#13;");
    EXPECT_EQ(xml_content_escaped(stringref("\x00", 1)), "&#0;");
    EXPECT_EQ(xml_content_escaped("\x1f"), "&#31;");
}

GTEST_MAIN_RUN_ALL_TESTS()
