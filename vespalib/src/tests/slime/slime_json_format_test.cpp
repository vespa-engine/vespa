// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
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

TEST_F("encode empty", Slime) {
    EXPECT_EQUAL("null", make_json(f, true));
    EXPECT_EQUAL("null\n", make_json(f, false));
}

TEST_F("encode nix", Slime) {
    f.setNix();
    EXPECT_EQUAL("null", make_json(f, true));
    EXPECT_EQUAL("null\n", make_json(f, false));
}

TEST_F("encode true", Slime) {
    f.setBool(true);
    EXPECT_EQUAL("true", make_json(f, true));
    EXPECT_EQUAL("true\n", make_json(f, false));
}

TEST_F("encode false", Slime) {
    f.setBool(false);
    EXPECT_EQUAL("false", make_json(f, true));
    EXPECT_EQUAL("false\n", make_json(f, false));
}

TEST_F("encode long", Slime) {
    f.setLong(12345);
    EXPECT_EQUAL("12345", make_json(f, true));
    EXPECT_EQUAL("12345\n", make_json(f, false));
}

TEST_F("encode double", Slime) {
    f.setDouble(0.5);
    EXPECT_EQUAL("0.5", make_json(f, true));
    EXPECT_EQUAL("0.5\n", make_json(f, false));
}

TEST_F("encode double nan", Slime) {
    f.setDouble(std::numeric_limits<double>::quiet_NaN());
    EXPECT_EQUAL("null", make_json(f, true));
    EXPECT_EQUAL("null\n", make_json(f, false));
}

TEST_F("encode double inf", Slime) {
    f.setDouble(std::numeric_limits<double>::infinity());
    EXPECT_EQUAL("null", make_json(f, true));
    EXPECT_EQUAL("null\n", make_json(f, false));
}

TEST_F("encode string", Slime) {
    f.setString("foo");
    EXPECT_EQUAL("\"foo\"", make_json(f, true));
    EXPECT_EQUAL("\"foo\"\n", make_json(f, false));
}

TEST_F("encode data", Slime) {
    char buf[8];
    for (int i = 0; i < 8; ++i) {
        buf[i] = ((i * 2) << 4) | (i * 2 + 1);
    }
    f.setData(Memory(buf, 8));
    EXPECT_EQUAL("\"0x0123456789ABCDEF\"", make_json(f, true));
    EXPECT_EQUAL("\"0x0123456789ABCDEF\"\n", make_json(f, false));
}

TEST_F("encode empty array", Slime) {
    Cursor &c = f.setArray();
    (void)c;
    EXPECT_EQUAL("[]", make_json(f, true));
    EXPECT_EQUAL("[\n"
                 "]\n", make_json(f, false));
}

TEST_F("encode empty object", Slime) {
    Cursor &c = f.setObject();
    (void)c;
    EXPECT_EQUAL("{}", make_json(f, true));
    EXPECT_EQUAL("{\n"
                 "}\n", make_json(f, false));
}

TEST_F("encode array", Slime) {
    Cursor &c = f.setArray();
    c.addLong(123);
    c.addDouble(0.5);
    c.addString("foo");
    c.addBool(true);
    EXPECT_EQUAL("[123,0.5,\"foo\",true]", make_json(f, true));
    EXPECT_EQUAL("[\n"
                 "    123,\n"
                 "    0.5,\n"
                 "    \"foo\",\n"
                 "    true\n"
                 "]\n", make_json(f, false));
}

TEST_F("encode object", Slime) {
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

TEST_F("encode nesting", Slime) {
    Cursor &c = f.setObject().setObject("a").setArray("b").addArray();
    c.addLong(1);
    c.addLong(2);
    c.addLong(3);
    EXPECT_EQUAL("{\"a\":{\"b\":[[1,2,3]]}}", make_json(f, true));
    EXPECT_EQUAL("{\n"
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

TEST_F("decode null", Slime) {
    EXPECT_TRUE(parse_json("null", f));
    EXPECT_EQUAL(vespalib::slime::NIX::ID, f.get().type().getId());
}

TEST_F("decode true", Slime) {
    EXPECT_TRUE(parse_json("true", f));
    EXPECT_EQUAL(vespalib::slime::BOOL::ID, f.get().type().getId());
    EXPECT_EQUAL(true, f.get().asBool());
}

TEST_F("decode false", Slime) {
    EXPECT_TRUE(parse_json("false", f));
    EXPECT_EQUAL(vespalib::slime::BOOL::ID, f.get().type().getId());
    EXPECT_EQUAL(false, f.get().asBool());
}

TEST("decode number") {
    EXPECT_EQUAL(0.0,  json_double("0"));
    EXPECT_EQUAL(1.0,  json_double("1"));
    EXPECT_EQUAL(2.0,  json_double("2"));
    EXPECT_EQUAL(3.0,  json_double("3"));
    EXPECT_EQUAL(4.0,  json_double("4"));
    EXPECT_EQUAL(5.0,  json_double("5"));
    EXPECT_EQUAL(6.0,  json_double("6"));
    EXPECT_EQUAL(7.0,  json_double("7"));
    EXPECT_EQUAL(8.0,  json_double("8"));
    EXPECT_EQUAL(9.0,  json_double("9"));
    EXPECT_EQUAL(-9.0, json_double("-9"));
    EXPECT_EQUAL(5.5,  json_double("5.5"));
    EXPECT_EQUAL(5e7,  json_double("5e7"));

    EXPECT_EQUAL(5L,   json_long("5"));
    EXPECT_EQUAL(5L,   json_long("5.5"));
    EXPECT_EQUAL(50000000L, json_long("5e7"));
    EXPECT_EQUAL(9223372036854775807L, json_long("9223372036854775807"));
}

TEST("decode string") {
    EXPECT_EQUAL(std::string("foo"), json_string("foo"));
    EXPECT_EQUAL(std::string("\""), json_string("\\\""));
    EXPECT_EQUAL(std::string("\b"), json_string("\\b"));
    EXPECT_EQUAL(std::string("\f"), json_string("\\f"));
    EXPECT_EQUAL(std::string("\n"), json_string("\\n"));
    EXPECT_EQUAL(std::string("\r"), json_string("\\r"));
    EXPECT_EQUAL(std::string("\t"), json_string("\\t"));

    EXPECT_EQUAL(std::string("A"), json_string("\\u0041"));
    EXPECT_EQUAL(std::string("\x0f"), json_string("\\u000f"));
    EXPECT_EQUAL(std::string("\x18"), json_string("\\u0018"));
    EXPECT_EQUAL(std::string("\x29"), json_string("\\u0029"));
    EXPECT_EQUAL(std::string("\x3a"), json_string("\\u003a"));
    EXPECT_EQUAL(std::string("\x4b"), json_string("\\u004b"));
    EXPECT_EQUAL(std::string("\x5c"), json_string("\\u005c"));
    EXPECT_EQUAL(std::string("\x6d"), json_string("\\u006d"));
    EXPECT_EQUAL(std::string("\x7e"), json_string("\\u007e"));

    EXPECT_EQUAL(std::string("\x7f"), json_string("\\u007f"));
    EXPECT_EQUAL(std::string("\xc2\x80"), json_string("\\u0080"));
    EXPECT_EQUAL(std::string("\xdf\xbf"), json_string("\\u07ff"));
    EXPECT_EQUAL(std::string("\xe0\xa0\x80"), json_string("\\u0800"));
    EXPECT_EQUAL(std::string("\xed\x9f\xbf"), json_string("\\ud7ff"));
    EXPECT_EQUAL(std::string("\xee\x80\x80"), json_string("\\ue000"));
    EXPECT_EQUAL(std::string("\xef\xbf\xbf"), json_string("\\uffff"));
    EXPECT_EQUAL(std::string("\xf0\x90\x80\x80"), json_string("\\ud800\\udc00"));
    EXPECT_EQUAL(std::string("\xf4\x8f\xbf\xbf"), json_string("\\udbff\\udfff"));
}

TEST_F("decode data", Slime) {
    EXPECT_TRUE(parse_json("x", f));
    EXPECT_EQUAL(vespalib::slime::DATA::ID, f.get().type().getId());
    Memory m = f.get().asData();
    EXPECT_EQUAL(0u, m.size);

    EXPECT_TRUE(parse_json("x0000", f));
    EXPECT_EQUAL(vespalib::slime::DATA::ID, f.get().type().getId());
    m = f.get().asData();
    EXPECT_EQUAL(2u, m.size);
    EXPECT_EQUAL(0, m.data[0]);
    EXPECT_EQUAL(0, m.data[1]);

    EXPECT_TRUE(parse_json("x1234567890abcdefABCDEF", f));
    EXPECT_EQUAL(vespalib::slime::DATA::ID, f.get().type().getId());
    m = f.get().asData();
    EXPECT_EQUAL(11u, m.size);
    EXPECT_EQUAL((char)0x12, m.data[0]);
    EXPECT_EQUAL((char)0x34, m.data[1]);
    EXPECT_EQUAL((char)0x56, m.data[2]);
    EXPECT_EQUAL((char)0x78, m.data[3]);
    EXPECT_EQUAL((char)0x90, m.data[4]);
    EXPECT_EQUAL((char)0xAB, m.data[5]);
    EXPECT_EQUAL((char)0xCD, m.data[6]);
    EXPECT_EQUAL((char)0xEF, m.data[7]);
    EXPECT_EQUAL((char)0xAB, m.data[8]);
    EXPECT_EQUAL((char)0xCD, m.data[9]);
    EXPECT_EQUAL((char)0xEF, m.data[10]);
}

TEST_F("decode empty array", Slime) {
    EXPECT_TRUE(parse_json("[]", f));
    EXPECT_EQUAL(vespalib::slime::ARRAY::ID, f.get().type().getId());
    EXPECT_EQUAL(0u, f.get().children());
}

TEST_F("decode empty object", Slime) {
    EXPECT_TRUE(parse_json("{}", f));
    EXPECT_EQUAL(vespalib::slime::OBJECT::ID, f.get().type().getId());
    EXPECT_EQUAL(0u, f.get().children());
}

TEST_F("decode array", Slime) {
    EXPECT_TRUE(parse_json("[123,0.5,\"foo\",true]", f));
    EXPECT_EQUAL(vespalib::slime::ARRAY::ID, f.get().type().getId());
    EXPECT_EQUAL(4u, f.get().children());
    EXPECT_EQUAL(123.0, f.get()[0].asDouble());
    EXPECT_EQUAL(0.5, f.get()[1].asDouble());
    EXPECT_EQUAL(std::string("foo"), f.get()[2].asString().make_string());
    EXPECT_EQUAL(true, f.get()[3].asBool());
}

TEST_F("decode object", Slime) {
    EXPECT_TRUE(parse_json("{\"a\":123,\"b\":0.5,\"c\":\"foo\",\"d\":true,\"e\":xff0011}", f));
    EXPECT_EQUAL(vespalib::slime::OBJECT::ID, f.get().type().getId());
    EXPECT_EQUAL(5u, f.get().children());
    EXPECT_EQUAL(123.0, f.get()["a"].asDouble());
    EXPECT_EQUAL(0.5, f.get()["b"].asDouble());
    EXPECT_EQUAL(std::string("foo"), f.get()["c"].asString().make_string());
    EXPECT_EQUAL(true, f.get()["d"].asBool());
    Memory m = f.get()["e"].asData();
    EXPECT_EQUAL(3u, m.size);
    EXPECT_EQUAL((char)255, m.data[0]);
    EXPECT_EQUAL((char)0,   m.data[1]);
    EXPECT_EQUAL((char)17,  m.data[2]);
}

TEST_F("decode nesting", Slime) {
    EXPECT_TRUE(parse_json("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}", f));
    EXPECT_EQUAL(1.0, f.get()["a"]["b"][0][0].asDouble());
    EXPECT_EQUAL(2.0, f.get()["a"]["b"][0][1].asDouble());
    EXPECT_EQUAL(3.0, f.get()["a"]["b"][0][2].asDouble());
    EXPECT_EQUAL(4.0, f.get()["a"]["c"][0][0].asDouble());
}

TEST("decode whitespace") {
    EXPECT_EQUAL(std::string("true"), normalize("\n\r\t true"));
    EXPECT_EQUAL(std::string("true"), normalize(" true "));
    EXPECT_EQUAL(std::string("false"), normalize(" false "));
    EXPECT_EQUAL(std::string("null"), normalize(" null "));
    EXPECT_EQUAL(std::string("\"foo\""), normalize(" \"foo\" "));
    EXPECT_EQUAL(std::string("{}"), normalize(" { } "));
    EXPECT_EQUAL(std::string("[]"), normalize(" [ ] "));
    EXPECT_EQUAL(std::string("5"), normalize(" 5 "));
    EXPECT_EQUAL(std::string("[1]"), normalize(" [ 1 ] "));
    EXPECT_EQUAL(std::string("[1,2,3]"), normalize(" [ 1 , 2 , 3 ] "));
    EXPECT_EQUAL(std::string("{\"a\":1}"), normalize(" { \"a\" : 1 } "));
    EXPECT_EQUAL(normalize("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}"),
                 normalize(" { \"a\" : { \"b\" : [ [ 1 , 2 , 3 ] ] , \"c\" : [ [ 4 ] ] } } "));
}

TEST("decode invalid input") {
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

TEST("decode simplified form") {
    EXPECT_EQUAL(std::string("\"foo\""), normalize("'foo'"));
    EXPECT_EQUAL(normalize("{\"a\":123,\"b\":0.5,\"c\":\"foo\",\"d\":true}"), normalize("{a:123,b:0.5,c:'foo',d:true}"));
    EXPECT_EQUAL(normalize("{\"a\":{\"b\":[[1,2,3]],\"c\":[[4]]}}"), normalize("{a:{b:[[1,2,3]],c:[[4]]}}"));
}

TEST_F("decode bytes not null-terminated", Slime) {
    std::ifstream file(TEST_PATH("large_json.txt"));
    ASSERT_TRUE(file.is_open());
    std::stringstream buf;
    buf << file.rdbuf();
    std::string str = buf.str();
    Memory mem(str.c_str(), 18911);
    EXPECT_TRUE(parse_json_bytes(mem, f));
}

TEST("require that multiple adjacent values can be decoded from a single input") {
    vespalib::string data("true{}false[]null\"foo\"'bar'1.5null");
    MemoryInput input(data);
    EXPECT_EQUAL(std::string("true"), normalize(input));
    EXPECT_EQUAL(std::string("{}"), normalize(input));
    EXPECT_EQUAL(std::string("false"), normalize(input));
    EXPECT_EQUAL(std::string("[]"), normalize(input));
    EXPECT_EQUAL(std::string("null"), normalize(input));
    EXPECT_EQUAL(std::string("\"foo\""), normalize(input));
    EXPECT_EQUAL(std::string("\"bar\""), normalize(input));
    EXPECT_EQUAL(std::string("1.5"), normalize(input));
    EXPECT_EQUAL(std::string("null"), normalize(input));
    EXPECT_EQUAL(input.obtain().size, 0u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
