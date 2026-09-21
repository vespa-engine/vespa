// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/string_escape.h>

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
    EXPECT_EQ(xml_attribute_escaped(std::string_view("\x00", 1)),
              "&#0;"); // Can't just invoke strlen with null byte :)
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
    EXPECT_EQ(xml_content_escaped(std::string_view("\x00", 1)), "&#0;");
    EXPECT_EQ(xml_content_escaped("\x1f"), "&#31;");
}

TEST(GenericEscapeTest, printable_ascii_chars_are_not_escaped) {
    EXPECT_EQ(escape("ABCDEFGHIJKLMNOPQRSTUVWXYZ"), "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
    EXPECT_EQ(escape("abcdefghijklmnopqrstuvwxyz"), "abcdefghijklmnopqrstuvwxyz");
    EXPECT_EQ(escape("0123456789"), "0123456789");
    // Note that we do not escape single quotes
    EXPECT_EQ(escape("!#$%&'()*+,-./:;<=>?@[~]^_`{}|"), "!#$%&'()*+,-./:;<=>?@[~]^_`{}|");
}

TEST(GenericEscapeTest, double_quote_and_backslash_chars_are_escaped) {
    EXPECT_EQ(escape(R"("\)"), R"(\"\\)");
}

TEST(GenericEscapeTest, special_named_control_chars_are_escaped) {
    EXPECT_EQ(escape(std::string_view("\0", 1)), "\\0");
    EXPECT_EQ(escape("\a\b\t\n\v\f\r"), R"(\a\b\t\n\v\f\r)");
}

TEST(GenericEscapeTest, non_printable_chars_are_hex_escaped) {
    std::array<uint8_t, 4> raw_u8 = { 1, 127, 128, 255 };
    std::string raw(reinterpret_cast<const char*>(raw_u8.data()), raw_u8.size());
    EXPECT_EQ(escape(raw), R"(\x01\x7f\x80\xff)");
}
