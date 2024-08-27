// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/objects/hexdump.h>
#include <initializer_list>
#include <ostream>

using vespalib::nbostream;
using vespalib::alloc::Alloc;

struct ExpBuffer {
    std::vector<uint8_t> buf;
    ExpBuffer(std::initializer_list<uint8_t> buf_in)
        : buf(buf_in)
    {
    }
    ExpBuffer(size_t size)
        : buf(size)
    {
    }
    bool operator==(const ExpBuffer& rhs) const noexcept { return buf == rhs.buf; }
    uint8_t* data() noexcept { return buf.data(); }
    const uint8_t* data() const noexcept { return buf.data(); }
    size_t size() const noexcept { return buf.size(); }
};

bool operator==(const ExpBuffer& exp, const nbostream& stream)
{
    return ((exp.size() == stream.size()) &&
            (memcmp(exp.data(), stream.peek(), exp.size()) == 0));
}

void PrintTo(const ExpBuffer& value, std::ostream* os) {
    *os << vespalib::HexDump(value.buf.data(), value.buf.size());
}

struct Fixture
{
    nbostream _stream;

    template <typename T>
    void
    assertSerialize(const ExpBuffer &exp, const T &val)
    {
        _stream << val;
        EXPECT_EQ(exp, _stream);
        T checkVal = T();
        _stream >> checkVal;
        EXPECT_EQ(val, checkVal);
    }
};

TEST(NbostreamTest, test_that_move_of_owned_buffer_does_not_copy)
{
    Alloc buf = Alloc::allocHeap(1000);
    const void * ptr = buf.get();
    nbostream os(std::move(buf), 0);
    os << static_cast<int64_t>(0x567);
    EXPECT_EQ(ptr, os.peek());
    EXPECT_EQ(8ul, os.size());
    nbostream moved(std::move(os));
    EXPECT_TRUE(nullptr == os.peek());
    EXPECT_EQ(0ul, os.size());
    EXPECT_EQ(ptr, moved.peek());
    EXPECT_EQ(8ul, moved.size());
    int64_t tmp(0);
    moved >> tmp;
    EXPECT_EQ(static_cast<int64_t>(0x567), tmp);
}

TEST(NbostreamTest, test_that_move_of_non_owned_buffer_does_copy)
{
    Alloc buf = Alloc::allocHeap(1000);
    const void * ptr = buf.get();
    nbostream os(std::move(buf), 0);
    os << static_cast<int64_t>(0x567);
    EXPECT_EQ(ptr, os.peek());
    EXPECT_EQ(8ul, os.size());
    nbostream refering(os.peek(), os.size());
    EXPECT_EQ(ptr, os.peek());
    EXPECT_EQ(8ul, os.size());
    EXPECT_EQ(ptr, refering.peek());
    EXPECT_EQ(8ul, refering.size());
    nbostream moved(std::move(refering));
    EXPECT_TRUE(nullptr == refering.peek());
    EXPECT_EQ(0ul, refering.size());
    EXPECT_TRUE(ptr != moved.peek());
    EXPECT_EQ(8ul, moved.size());
    int64_t tmp(0);
    moved >> tmp;
    EXPECT_EQ(static_cast<int64_t>(0x567), tmp);
}

TEST(NbostreamTest, test_serializing_64_bit_signed_integers)
{
    Fixture f;
    int64_t val = 0x0123456789ABCDEF;
    f.assertSerialize({ 0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF }, val);
}


TEST(NbostreamTest, test_serializing_64_bit_unsigned_integers)
{
    Fixture f;
    uint64_t val = 0x0123456789ABCDEF;
    f.assertSerialize({ 0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF }, val);
}


TEST(NbostreamTest, test_serializing_32_bit_signed_integers)
{
    Fixture f;
    int32_t val = 0x01234567;
    f.assertSerialize({ 0x01, 0x23, 0x45, 0x67 }, val);
}


TEST(NbostreamTest, test_serializing_32_bit_unsigned_integers)
{
    Fixture f;
    uint32_t val = 0x01234567;
    f.assertSerialize({ 0x01, 0x23, 0x45, 0x67 }, val);
}

TEST(NbostreamTest, test_serializing_16_bit_signed_integers)
{
    Fixture f;
    int16_t val = 0x0123;
    f.assertSerialize({ 0x01, 0x23 }, val);
}


TEST(NbostreamTest, test_serializing_16_bit_unsigned_integers)
{
    Fixture f;
    uint16_t val = 0x0123;
    f.assertSerialize({ 0x01, 0x23 }, val);
}

TEST(NbostreamTest, test_serializing_8_bit_signed_integers)
{
    Fixture f;
    int8_t val = 0x23;
    f.assertSerialize({ 0x23 }, val);
}


TEST(NbostreamTest, test_serializing_8_bit_unsigned_integers)
{
    Fixture f;
    uint8_t val = 0x23;
    f.assertSerialize({ 0x23 }, val);
}

TEST(NbostreamTest, test_serializing_char)
{
    Fixture f;
    char val('A');
    f.assertSerialize({ 0x41 }, val);
}

TEST(NbostreamTest, test_serializing_bool)
{
    Fixture f;
    bool myfalse = false;
    bool mytrue = true;
    ExpBuffer exp({ 0x00, 0x01 });
    f._stream << myfalse << mytrue;
    EXPECT_EQ(exp, f._stream);
    bool checkFalse = true;
    bool checkTrue = false;
    f._stream >> checkFalse >> checkTrue;
    EXPECT_FALSE(checkFalse);
    EXPECT_TRUE(checkTrue);
}

TEST(NbostreamTest, test_serializing_double)
{
    Fixture f;
    double val = 1.5;
    f.assertSerialize({ 0x3F, 0xF8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, val);
}


TEST(NbostreamTest, test_serializing_float)
{
    Fixture f;
    float val = -1.5;
    f.assertSerialize({ 0xBF, 0xC0, 0x00, 0x00 }, val);
}

TEST(NbostreamTest, Test_serializing_c_string)
{
    Fixture f;
    const char *cstr = "Hello";
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    f._stream << cstr;
    EXPECT_EQ(exp, f._stream);
}

TEST(NbostreamTest, Test_serializing_std_string_view)
{
    Fixture f;
    std::string_view val("Hello");
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    f._stream << val;
    EXPECT_EQ(exp, f._stream);
}

TEST(NbostreamTest, Test_serializing_std_string)
{
    Fixture f;
    std::string val("Hello");
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    f.assertSerialize(exp, val);
}

TEST(NbostreamTest, Test_serializing_std_vector)
{
    Fixture f;
    std::vector<int16_t> val({ 0x0123, 0x4567 });
    ExpBuffer exp({ 0x00, 0x00, 0x00, 0x02, 0x01, 0x23, 0x45, 0x67 });
    f.assertSerialize(exp, val);
}

TEST(NbostreamTest, Test_serializing_std_pair)
{
    Fixture f;
    std::pair<int16_t, int16_t> val({ 0x0123, 0x4567 });
    ExpBuffer exp({ 0x01, 0x23, 0x45, 0x67 });
    f.assertSerialize(exp, val);
}

TEST(NbostreamTest, Test_write)
{
    Fixture f;
    f._stream.write("Hello", 5);
    ExpBuffer exp({ 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    EXPECT_EQ(exp, f._stream);
    EXPECT_EQ(5u, f._stream.size());
    ExpBuffer rval(5);
    f._stream.read(rval.data(), 5);
    EXPECT_EQ(exp, rval);
}


TEST(NbostreamTest, Test_putInt1_4)
{
    Fixture f;
    f._stream.putInt1_4Bytes(5);
    EXPECT_EQ(ExpBuffer({ 0x05 }), f._stream);
    uint32_t checkInt = f._stream.getInt1_4Bytes();
    EXPECT_EQ(5u, checkInt);
    EXPECT_EQ(0u, f._stream.size());
    f._stream.clear();
    f._stream.putInt1_4Bytes(1000);
    EXPECT_EQ(ExpBuffer({ 0x80, 0x00, 0x03, 0xe8 }), f._stream);
    checkInt = f._stream.getInt1_4Bytes();
    EXPECT_EQ(1000u, checkInt);
    EXPECT_EQ(0u, f._stream.size());
}


TEST(NbostreamTest, Test_writeSmallString)
{
    Fixture f;
    f._stream.writeSmallString("Hello");
    ExpBuffer exp({ 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f });
    EXPECT_EQ(exp, f._stream);
    std::string checkString;
    f._stream.readSmallString(checkString);
    EXPECT_EQ("Hello", checkString);
    EXPECT_EQ(0u, f._stream.size());
}

GTEST_MAIN_RUN_ALL_TESTS()
