// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/input.h>
#include <vespa/vespalib/data/memory_input.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <iostream>
#include <fstream>

using namespace vespalib::slime::convenience;
using vespalib::Input;
using vespalib::MemoryInput;

std::string make_json(const Slime &slime, bool compact) {
    vespalib::SimpleBuffer buf;
    vespalib::slime::JsonFormat::encode(slime, buf, compact);
    return buf.get().make_string();
}

bool parse_json(const std::string &json, Slime &slime) {
    size_t size = vespalib::slime::JsonFormat::decode(json, slime);
    if (size == 0) {
        fprintf(stderr, "json parsing failed:\n%s", make_json(slime, false).c_str());
    }
    return (size > 0);
}

bool parse_json_bytes(const Memory & json, Slime &slime) {
    size_t size = vespalib::slime::JsonFormat::decode(json, slime);
    if (size == 0) {
        fprintf(stderr, "json parsing failed:\n%s", make_json(slime, false).c_str());
    }
    return (size > 0);
}

double json_double(const std::string &str) {
    Slime slime;
    if (vespalib::slime::JsonFormat::decode(str, slime) != str.size()) {
        fprintf(stderr, "json number parsing failed:\n%s", make_json(slime, false).c_str());
        return 666.0;
    }
    return slime.get().asDouble();
}

int64_t json_long(const std::string &str) {
    Slime slime;
    if (vespalib::slime::JsonFormat::decode(str, slime) != str.size()) {
        fprintf(stderr, "json number parsing failed:\n%s", make_json(slime, false).c_str());
        return 666;
    }
    return slime.get().asLong();
}

std::string json_string(const std::string &str) {
    Slime slime;
    std::string quoted("\"");
    quoted.append(str);
    quoted.append("\"");
    if (vespalib::slime::JsonFormat::decode(quoted, slime) != quoted.size()) {
        fprintf(stderr, "json string parsing failed:\n%s", make_json(slime, false).c_str());
        return "<error>";
    }
    return slime.get().asString().make_string();
}

std::string normalize(const std::string &json) {
    Slime slime;
    EXPECT_TRUE(vespalib::slime::JsonFormat::decode(json, slime) > 0);
    return make_json(slime, true);
}

std::string normalize(Input &input) {
    Slime slime;
    EXPECT_TRUE(vespalib::slime::JsonFormat::decode(input, slime) > 0);
    return make_json(slime, true);
}

bool check_valid(const std::string &json) {
    Slime slime;
    return (vespalib::slime::JsonFormat::decode(json, slime) > 0);
}

TEST(SlimeJsonFormatTest, encode_empty) {
    Slime f;
    EXPECT_EQ("null", make_json(f, true));
    EXPECT_EQ("null\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_nix) {
    Slime f;
    f.setNix();
    EXPECT_EQ("null", make_json(f, true));
    EXPECT_EQ("null\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_true) {
    Slime f;
    f.setBool(true);
    EXPECT_EQ("true", make_json(f, true));
    EXPECT_EQ("true\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_false) {
    Slime f;
    f.setBool(false);
    EXPECT_EQ("false", make_json(f, true));
    EXPECT_EQ("false\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_long) {
    Slime f;
    f.setLong(12345);
    EXPECT_EQ("12345", make_json(f, true));
    EXPECT_EQ("12345\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_double) {
    Slime f;
    f.setDouble(0.5);
    EXPECT_EQ("0.5", make_json(f, true));
    EXPECT_EQ("0.5\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_double_nan) {
    Slime f;
    f.setDouble(std::numeric_limits<double>::quiet_NaN());
    EXPECT_EQ("null", make_json(f, true));
    EXPECT_EQ("null\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_double_inf) {
    Slime f;
    f.setDouble(std::numeric_limits<double>::infinity());
    EXPECT_EQ("null", make_json(f, true));
    EXPECT_EQ("null\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_string) {
    Slime f;
    f.setString("foo");
    EXPECT_EQ("\"foo\"", make_json(f, true));
    EXPECT_EQ("\"foo\"\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_data) {
    Slime f;
    char buf[8];
    for (int i = 0; i < 8; ++i) {
        buf[i] = ((i * 2) << 4) | (i * 2 + 1);
    }
    f.setData(Memory(buf, 8));
    EXPECT_EQ("\"0x0123456789ABCDEF\"", make_json(f, true));
    EXPECT_EQ("\"0x0123456789ABCDEF\"\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_empty_array) {
    Slime f;
    Cursor &c = f.setArray();
    (void)c;
    EXPECT_EQ("[]", make_json(f, true));
    EXPECT_EQ("[\n"
                 "]\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_empty_object) {
    Slime f;
    Cursor &c = f.setObject();
    (void)c;
    EXPECT_EQ("{}", make_json(f, true));
    EXPECT_EQ("{\n"
                 "}\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_array) {
    Slime f;
    Cursor &c = f.setArray();
    c.addLong(123);
    c.addDouble(0.5);
    c.addString("foo");
    c.addBool(true);
    EXPECT_EQ("[123,0.5,\"foo\",true]", make_json(f, true));
    EXPECT_EQ("[\n"
                 "    123,\n"
                 "    0.5,\n"
                 "    \"foo\",\n"
                 "    true\n"
                 "]\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, encode_object) {
    Slime f;
    Cursor &c = f.setObject();
    c.setLong("a", 10);
    EXPECT_TRUE(c.valid());
    c.setLong("b", 20);
    EXPECT_TRUE(("{\"b\":20,\"a\":10}" == make_json(f, true)) ||
                ("{\"a\":10,\"b\":20}" == make_json(f, true)));
    EXPECT_TRUE(("{\n"
                 "    \"b\": 20,\n"
                 "    \"a\": 10\n"
                 "}\n" == make_json(f, false)) ||
                ("{\n"
                 "    \"a\": 10,\n"
                 "    \"b\": 20\n"
                 "}\n" == make_json(f, false)));
}

TEST(SlimeJsonFormatTest, encode_nesting) {
    Slime f;
    Cursor &c = f.setObject().setObject("a").setArray("b").addArray();
    c.addLong(1);
    c.addLong(2);
    c.addLong(3);
    EXPECT_EQ("{\"a\":{\"b\":[[1,2,3]]}}", make_json(f, true));
    EXPECT_EQ("{\n"
                 "    \"a\": {\n"
                 "        \"b\": [\n"
                 "            [\n"
                 "                1,\n"
                 "                2,\n"
                 "                3\n"
                 "            ]\n"
                 "        ]\n"
                 "    }\n"
                 "}\n", make_json(f, false));
}

TEST(SlimeJsonFormatTest, decode_null) {
    Slime f;
    EXPECT_TRUE(parse_json("null", f));
    EXPECT_EQ(vespalib::slime::NIX::ID, f.get().type().getId());
}

TEST(SlimeJsonFormatTest, decode_true) {
    Slime f;
    EXPECT_TRUE(parse_json("true", f));
    EXPECT_EQ(vespalib::slime::BOOL::ID, f.get().type().getId());
    EXPECT_EQ(true, f.get().asBool());
}

TEST(SlimeJsonFormatTest, decode_false) {
    Slime f;
    EXPECT_TRUE(parse_json("false", f));
    EXPECT_EQ(vespalib::slime::BOOL::ID, f.get().type().getId());
    EXPECT_EQ(false, f.get().asBool());
}

TEST(SlimeJsonFormatTest, decode_number) {
    EXPECT_EQ(0.0,  json_double("0"));
    EXPECT_EQ(1.0,  json_double("1"));
    EXPECT_EQ(2.0,  json_double("2"));
    EXPECT_EQ(3.0,  json_double("3"));
    EXPECT_EQ(4.0,  json_double("4"));
    EXPECT_EQ(5.0,  json_double("5"));
    EXPECT_EQ(6.0,  json_double("6"));
    EXPECT_EQ(7.0,  json_double("7"));
    EXPECT_EQ(8.0,  json_double("8"));
    EXPECT_EQ(9.0,  json_double("9"));
    EXPECT_EQ(-9.0, json_double("-9"));
    EXPECT_EQ(5.5,  json_double("5.5"));
    EXPECT_EQ(5e7,  json_double("5e7"));

    EXPECT_EQ(5L,   json_long("5"));
    EXPECT_EQ(5L,   json_long("5.5"));
    EXPECT_EQ(50000000L, json_long("5e7"));
    EXPECT_EQ(9223372036854775807L, json_long("9223372036854775807"));
}

TEST(SlimeJsonFormatTest, decode_string) {
    EXPECT_EQ(std::string("foo"), json_string("foo"));
    EXPECT_EQ(std::string("\""), json_string("\\\""));
    EXPECT_EQ(std::string("\b"), json_string("\\b"));
    EXPECT_EQ(std::string("\f"), json_string("\\f"));
    EXPECT_EQ(std::string("\n"), json_string("\\n"));
    EXPECT_EQ(std::string("\r"), json_string("\\r"));
    EXPECT_EQ(std::string("\t"), json_string("\\t"));

    EXPECT_EQ(std::string("A"), json_string("\\u0041"));
    EXPECT_EQ(std::string("\x0f"), json_string("\\u000f"));
    EXPECT_EQ(std::string("\x18"), json_string("\\u0018"));
    EXPECT_EQ(std::string("\x29"), json_string("\\u0029"));
    EXPECT_EQ(std::string("\x3a"), json_string("\\u003a"));
    EXPECT_EQ(std::string("\x4b"), json_string("\\u004b"));
    EXPECT_EQ(std::string("\x5c"), json_string("\\u005c"));
    EXPECT_EQ(std::string("\x6d"), json_string("\\u006d"));
    EXPECT_EQ(std::string("\x7e"), json_string("\\u007e"));

    EXPECT_EQ(std::string("\x7f"), json_string("\\u007f"));
    EXPECT_EQ(std::string("\xc2\x80"), json_string("\\u0080"));
    EXPECT_EQ(std::string("\xdf\xbf"), json_string("\\u07ff"));
    EXPECT_EQ(std::string("\xe0\xa0\x80"), json_string("\\u0800"));
    EXPECT_EQ(std::string("\xed\x9f\xbf"), json_string("\\ud7ff"));
    EXPECT_EQ(std::string("\xee\x80\x80"), json_string("\\ue000"));
    EXPECT_EQ(std::string("\xef\xbf\xbf"), json_string("\\uffff"));
    EXPECT_EQ(std::string("\xf0\x90\x80\x80"), json_string("\\ud800\\udc00"));
    EXPECT_EQ(std::string("\xf4\x8f\xbf\xbf"), json_string("\\udbff\\udfff"));
}

TEST(SlimeJsonFormatTest, decode_data) {
    Slime f;
    EXPECT_TRUE(parse_json("x", f));
    EXPECT_EQ(vespalib::slime::DATA::ID, f.get().type().getId());
    Memory m = f.get().asData();
    EXPECT_EQ(0u, m.size);

    EXPECT_TRUE(parse_json("x0000", f));
    EXPECT_EQ(vespalib::slime::DATA::ID, f.get().type().getId());
    m = f.get().asData();
    EXPECT_EQ(2u, m.size);
    EXPECT_EQ(0, m.data[0]);
    EXPECT_EQ(0, m.data[1]);

    EXPECT_TRUE(parse_json("x1234567890abcdefABCDEF", f));
    EXPECT_EQ(vespalib::slime::DATA::ID, f.get().type().getId());
    m = f.get().asData();
    EXPECT_EQ(11u, m.size);
    EXPECT_EQ((char)0x12, m.data[0]);
    EXPECT_EQ((char)0x34, m.data[1]);
    EXPECT_EQ((char)0x56, m.data[2]);
    EXPECT_EQ((char)0x78, m.data[3]);
    EXPECT_EQ((char)0x90, m.data[4]);
    EXPECT_EQ((char)0xAB, m.data[5]);
    EXPECT_EQ((char)0xCD, m.data[6]);
    EXPECT_EQ((char)0xEF, m.data[7]);
    EXPECT_EQ((char)0xAB, m.data[8]);
    EXPECT_EQ((char)0xCD, m.data[9]);
    EXPECT_EQ((char)0xEF, m.data[10]);
}

TEST(SlimeJsonFormatTest, decode_empty_array) {
    Slime f;
    EXPECT_TRUE(parse_json("[]", f));
    EXPECT_EQ(vespalib::slime::ARRAY::ID, f.get().type().getId());
    EXPECT_EQ(0u, f.get().children());
}

TEST(SlimeJsonFormatTest, decode_empty_object) {
    Slime f;
    EXPECT_TRUE(parse_json("{}", f));
    EXPECT_EQ(vespalib::slime::OBJECT::ID, f.get().type().getId());
    EXPECT_EQ(0u, f.get().children());
}

TEST(SlimeJsonFormatTest, decode_array) {
    Slime f;
    EXPECT_TRUE(parse_json("[123,0.5,\"foo\",true]", f));
    EXPECT_EQ(vespalib::slime::ARRAY::ID, f.get().type().getId());
    EXPECT_EQ(4u, f.get().children());
    EXPECT_EQ(123.0, f.get()[0].asDouble());
    EXPECT_EQ(0.5, f.get()[1].asDouble());
    EXPECT_EQ(std::string("foo"), f.get()[2].asString().make_string());
    EXPECT_EQ(true, f.get()[3].asBool());
}

TEST(SlimeJsonFormatTest, decode_object) {
    Slime f;
    EXPECT_TRUE(parse_json("{\"a\":123,\"b\":0.5,\"c\":\"foo\",\"d\":true,\"e\":xff0011}", f));
    EXPECT_EQ(vespalib::slime::OBJECT::ID, f.get().type().getId());
    EXPECT_EQ(5u, f.get().children());
    EXPECT_EQ(123.0, f.get()["a"].asDouble());
    EXPECT_EQ(0.5, f.get()["b"].asDouble());
    EXPECT_EQ(std::string("foo"), f.get()["c"].asString().make_string());
    EXPECT_EQ(true, f.get()["d"].asBool());
    Memory m = f.get()["e"].asData();
    EXPECT_EQ(3u, m.size);
    EXPECT_EQ((char)255, m.data[0]);
    EXPECT_EQ((char)0,   m.data[1]);
    EXPECT_EQ((char)17,  m.data[2]);
}

TEST(SlimeJsonFormatTest, decode_nesting) {
    Slime f;
    EXPECT_TRUE(parse_json("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}", f));
    EXPECT_EQ(1.0, f.get()["a"]["b"][0][0].asDouble());
    EXPECT_EQ(2.0, f.get()["a"]["b"][0][1].asDouble());
    EXPECT_EQ(3.0, f.get()["a"]["b"][0][2].asDouble());
    EXPECT_EQ(4.0, f.get()["a"]["c"][0][0].asDouble());
}

TEST(SlimeJsonFormatTest, decode_whitespace) {
    EXPECT_EQ(std::string("true"), normalize("\n\r\t true"));
    EXPECT_EQ(std::string("true"), normalize(" true "));
    EXPECT_EQ(std::string("false"), normalize(" false "));
    EXPECT_EQ(std::string("null"), normalize(" null "));
    EXPECT_EQ(std::string("\"foo\""), normalize(" \"foo\" "));
    EXPECT_EQ(std::string("{}"), normalize(" { } "));
    EXPECT_EQ(std::string("[]"), normalize(" [ ] "));
    EXPECT_EQ(std::string("5"), normalize(" 5 "));
    EXPECT_EQ(std::string("[1]"), normalize(" [ 1 ] "));
    EXPECT_EQ(std::string("[1,2,3]"), normalize(" [ 1 , 2 , 3 ] "));
    EXPECT_EQ(std::string("{\"a\":1}"), normalize(" { \"a\" : 1 } "));
    EXPECT_EQ(normalize("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}"),
                 normalize(" { \"a\" : { \"b\" : [ [ 1 , 2 , 3 ] ] , \"c\" : [ [ 4 ] ] } } "));
}

TEST(SlimeJsonFormatTest, decode_invalid_input) {
    EXPECT_TRUE(!check_valid(""));
    EXPECT_TRUE(!check_valid("["));
    EXPECT_TRUE(!check_valid("{"));
    EXPECT_TRUE(!check_valid("]"));
    EXPECT_TRUE(!check_valid("}"));
    EXPECT_TRUE(!check_valid("{]"));
    EXPECT_TRUE(!check_valid("[}"));
    EXPECT_TRUE(!check_valid("+5"));
    EXPECT_TRUE(!check_valid("fals"));
    EXPECT_TRUE(!check_valid("tru"));
    EXPECT_TRUE(!check_valid("nul"));
    EXPECT_TRUE(!check_valid("bar"));
    EXPECT_TRUE(!check_valid("\"bar"));
    EXPECT_TRUE(!check_valid("bar\""));
    EXPECT_TRUE(!check_valid("'bar\""));
    EXPECT_TRUE(!check_valid("\"bar'"));
    EXPECT_TRUE(!check_valid("{\"foo"));
}

TEST(SlimeJsonFormatTest, decode_simplified_form) {
    EXPECT_EQ(std::string("\"foo\""), normalize("'foo'"));
    EXPECT_EQ(normalize("{\"a\":123,\"b\":0.5,\"c\":\"foo\",\"d\":true}"), normalize("{a:123,b:0.5,c:'foo',d:true}"));
    EXPECT_EQ(normalize("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}"), normalize("{a:{b:[[1,2,3]],c:[[4]]}}"));
}

TEST(SlimeJsonFormatTest, decode_bytes_not_null_terminated) {
    Slime f;
    std::ifstream file(TEST_PATH("large_json.txt"));
    ASSERT_TRUE(file.is_open());
    std::stringstream buf;
    buf << file.rdbuf();
    std::string str = buf.str();
    Memory mem(str.c_str(), 18911);
    EXPECT_TRUE(parse_json_bytes(mem, f));
}

TEST(SlimeJsonFormatTest, require_that_multiple_adjacent_values_can_be_decoded_from_a_single_input) {
    std::string data("true{}false[]null\"foo\"'bar'1.5null");
    MemoryInput input(data);
    EXPECT_EQ(std::string("true"), normalize(input));
    EXPECT_EQ(std::string("{}"), normalize(input));
    EXPECT_EQ(std::string("false"), normalize(input));
    EXPECT_EQ(std::string("[]"), normalize(input));
    EXPECT_EQ(std::string("null"), normalize(input));
    EXPECT_EQ(std::string("\"foo\""), normalize(input));
    EXPECT_EQ(std::string("\"bar\""), normalize(input));
    EXPECT_EQ(std::string("1.5"), normalize(input));
    EXPECT_EQ(std::string("null"), normalize(input));
    EXPECT_EQ(input.obtain().size, 0u);
}

GTEST_MAIN_RUN_ALL_TESTS()
