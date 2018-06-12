// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <ostream>

using vespalib::nbostream;
using vespalib::alloc::Alloc;
using ExpBuffer = std::vector<uint8_t>;

namespace std
{

bool operator==(const std::vector<uint8_t> &exp, const nbostream &stream)
{
    return ((exp.size() == stream.size()) &&
            (memcmp(&exp[0], stream.peek(), exp.size()) == 0));
}

std::ostream &operator<<(std::ostream &out, const std::vector<uint8_t> &rhs)
{
    out << vespalib::HexDump(&rhs[0], rhs.size());
    return out;
}

template <typename T>
std::ostream &
operator<<(std::ostream &os, const vespalib::Array<T> &set)
{
    os << "{";
    bool first = true;
    for (const auto &entry : set) {
        if (!first) {
            os << ",";
        }
        os << entry;
        first = false;
    }
    os << "}";
    return os;
}


} // namespace std

struct Fixture
{
    nbostream _stream;

    template <typename T>
    void
    assertSerialize(const ExpBuffer &exp, const T &val)
    {
        _stream << val;
        EXPECT_EQUAL(exp, _stream);
        T checkVal = T();
        _stream >> checkVal;
        EXPECT_EQUAL(val, checkVal);
    }
};

TEST("test that move of owned buffer does not copy") {
    Alloc buf = Alloc::allocHeap(1000);
    const void * ptr = buf.get();
    nbostream os(std::move(buf), 0);
    os << static_cast<long>(0x567);
    EXPECT_EQUAL(ptr, os.peek());
    EXPECT_EQUAL(8ul, os.size());
    nbostream moved(std::move(os));
    EXPECT_TRUE(nullptr == os.peek());
    EXPECT_EQUAL(0ul, os.size());
    EXPECT_EQUAL(ptr, moved.peek());
    EXPECT_EQUAL(8ul, moved.size());
    long tmp(0);
    moved >> tmp;
    EXPECT_EQUAL(0x567l, tmp);
}

TEST("test that move of non-owned buffer does copy") {
    Alloc buf = Alloc::allocHeap(1000);
    const void * ptr = buf.get();
    nbostream os(std::move(buf), 0);
    os << static_cast<long>(0x567);
    EXPECT_EQUAL(ptr, os.peek());
    EXPECT_EQUAL(8ul, os.size());
    nbostream refering(os.peek(), os.size());
    EXPECT_EQUAL(ptr, os.peek());
    EXPECT_EQUAL(8ul, os.size());
    EXPECT_EQUAL(ptr, refering.peek());
    EXPECT_EQUAL(8ul, refering.size());
    nbostream moved(std::move(refering));
    EXPECT_TRUE(nullptr == refering.peek());
    EXPECT_EQUAL(0ul, refering.size());
    EXPECT_TRUE(ptr != moved.peek());
    EXPECT_EQUAL(8ul, moved.size());
    long tmp(0);
    moved >> tmp;
    EXPECT_EQUAL(0x567l, tmp);
}

TEST_F("test serializing 64-bit signed integers", Fixture)
{
    int64_t val = 0x0123456789ABCDEF;
    f.assertSerialize({ 0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF }, val);
}


TEST_F("test serializing 64-bit unsigned integers", Fixture)
{
    uint64_t val = 0x0123456789ABCDEF;
    f.assertSerialize({ 0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF }, val);
}


TEST_F("test serializing 32-bit signed integers", Fixture)
{
    int32_t val = 0x01234567;
    f.assertSerialize({ 0x01, 0x23, 0x45, 0x67 }, val);
}


TEST_F("test serializing 32-bit unsigned integers", Fixture)
{
    uint32_t val = 0x01234567;
    f.assertSerialize({ 0x01, 0x23, 0x45, 0x67 }, val);
}

TEST_F("test serializing 16-bit signed integers", Fixture)
{
    int16_t val = 0x0123;
    f.assertSerialize({ 0x01, 0x23 }, val);
}


TEST_F("test serializing 16-bit unsigned integers", Fixture)
{
    uint16_t val = 0x0123;
    f.assertSerialize({ 0x01, 0x23 }, val);
}

TEST_F("test serializing 8-bit signed integers", Fixture)
{
    int8_t val = 0x23;
    f.assertSerialize({ 0x23 }, val);
}


TEST_F("test serializing 8-bit unsigned integers", Fixture)
{
    uint8_t val = 0x23;
    f.assertSerialize({ 0x23 }, val);
}

TEST_F("test serializing char", Fixture)
{
    char val('A');
    f.assertSerialize({ 0x41 }, val);
}

TEST_F("test serializing bool", Fixture)
{
    bool myfalse = false;
    bool mytrue = true;
    ExpBuffer exp({ 0x00, 0x01 });
    f._stream << myfalse << mytrue;
    EXPECT_EQUAL(exp, f._stream);
    bool checkFalse = true;
    bool checkTrue = false;
    f._stream >> checkFalse >> checkTrue;
    EXPECT_FALSE(checkFalse);
    EXPECT_TRUE(checkTrue);
}

TEST_F("test serializing double", Fixture)
{
    double val = 1.5;
    f.assertSerialize({ 0x3F, 0xF8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, val);
}


TEST_F("test serializing float", Fixture)
{
    float val = -1.5;
    f.assertSerialize({ 0xBF, 0xC0, 0x00, 0x00 }, val);
}

TEST_F("Test serializing c string", Fixture)
{
    const char *cstr = "Hello";
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    f._stream << cstr;
    EXPECT_EQUAL(exp, f._stream);
}

TEST_F("Test serializing stringref", Fixture)
{
    vespalib::stringref val("Hello");
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    f._stream << val;
    EXPECT_EQUAL(exp, f._stream);
}

TEST_F("Test serializing std::string", Fixture)
{
    std::string val("Hello");
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    f.assertSerialize(exp, val);
}

TEST_F("Test serializing vespalib::string", Fixture)
{
    vespalib::string val("Hello");
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    f.assertSerialize(exp, val);
}

TEST_F("Test serializing vespalib::Array", Fixture)
{
    vespalib::Array<int16_t> val;
    val.resize(2);
    val[0] = 0x0123;
    val[1] = 0x4567;
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x02, 0x01, 0x23, 0x45, 0x67 });
    f.assertSerialize(exp, val);
}

TEST_F("Test serializing std::vector", Fixture)
{
    std::vector<int16_t> val({ 0x0123, 0x4567 });
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x02, 0x01, 0x23, 0x45, 0x67 });
    f.assertSerialize(exp, val);
}

TEST_F("Test serializing std::pair", Fixture)
{
    std::pair<int16_t, int16_t> val({ 0x0123, 0x4567 });
    ExpBuffer exp({ 0x01, 0x23, 0x45, 0x67 });
    f.assertSerialize(exp, val);
}

TEST_F("Test write", Fixture)
{
    f._stream.write("Hello", 5);
    ExpBuffer exp({ 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    EXPECT_EQUAL(exp, f._stream);
    EXPECT_EQUAL(5u, f._stream.size());
    ExpBuffer rval(5);
    f._stream.read(&rval[0], 5);
    EXPECT_EQUAL(exp, rval);
}


TEST_F("Test putInt1_4", Fixture)
{
    f._stream.putInt1_4Bytes(5);
    EXPECT_EQUAL(ExpBuffer({ 0x05 }), f._stream);
    uint32_t checkInt = f._stream.getInt1_4Bytes();
    EXPECT_EQUAL(5u, checkInt);
    EXPECT_EQUAL(0u, f._stream.size());
    f._stream.clear();
    f._stream.putInt1_4Bytes(1000);
    EXPECT_EQUAL(ExpBuffer({ 0x80, 0x00, 0x03, 0xe8 }), f._stream);
    checkInt = f._stream.getInt1_4Bytes();
    EXPECT_EQUAL(1000u, checkInt);
    EXPECT_EQUAL(0u, f._stream.size());
}


TEST_F("Test writeSmallString", Fixture)
{
    f._stream.writeSmallString("Hello");
    ExpBuffer exp({ 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    EXPECT_EQUAL(exp, f._stream);
    vespalib::string checkString;
    f._stream.readSmallString(checkString);
    EXPECT_EQUAL("Hello", checkString);
    EXPECT_EQUAL(0u, f._stream.size());
}


TEST_MAIN() { TEST_RUN_ALL(); }
