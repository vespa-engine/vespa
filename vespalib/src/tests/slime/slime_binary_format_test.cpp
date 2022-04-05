// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include "type_traits.h"
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib::slime::convenience;
using namespace vespalib::slime::binary_format;
using namespace vespalib::slime;
using namespace vespalib;

//-----------------------------------------------------------------------------

const uint32_t TYPE_LIMIT = 8;     // 3 bits for type
const uint32_t META_LIMIT = 32;    // 5 bits for meta
const uint32_t MAX_CMPR_SIZE = 10; // 70 bits
const uint32_t MAX_NUM_SIZE = 8;   // int64_t or double
const uint32_t HEX_COUNT  = 25;    // bytes per line in hex dump

//-----------------------------------------------------------------------------

struct MemCmp {
    Memory memory;
    explicit MemCmp(const Memory &mem) : memory(mem) {}
    bool operator==(const MemCmp &rhs) const {
        if (memory.size != rhs.memory.size) {
            return false;
        }
        for (size_t i = 0; i < memory.size; ++i) {
            if (memory.data[i] != rhs.memory.data[i]) {
                return false;
            }
        }
        return true;
    }
};

std::ostream &operator<<(std::ostream &os, const MemCmp &obj) {
    uint32_t written = 0;
    os << "size: " << obj.memory.size << "(bytes)" << std::endl;
    for (size_t i = 0; i < obj.memory.size; ++i, ++written) {
        if (written > HEX_COUNT) {
            os << std::endl;
            written = 0;
        }
        os << vespalib::make_string("0x%02x ", obj.memory.data[i] & 0xff);
    }
    if (written > 0) {
        os << std::endl;
    }
    return os;
}

//-----------------------------------------------------------------------------

void verify_cmpr_ulong(uint64_t value, SimpleBuffer expect) {
    SimpleBuffer buf1;
    SimpleBuffer buf2;
    { // use direct low-level encode
        char tmp[MAX_CMPR_SIZE];
        uint32_t len = encode_cmpr_ulong(tmp, value);
        for (size_t i = 0; i < len; ++i) {
            buf1.add(tmp[i]);
        }
    }
    { // use write API
        OutputWriter out(buf2, 32);
        write_cmpr_ulong(out, value);
    }
    EXPECT_EQUAL(MemCmp(expect.get()), MemCmp(buf1.get()));
    EXPECT_EQUAL(MemCmp(expect.get()), MemCmp(buf2.get()));
    {
        InputReader input(expect);
        EXPECT_EQUAL(value, read_cmpr_ulong(input));
        EXPECT_EQUAL(input.get_offset(), buf1.get().size);
        EXPECT_TRUE(!input.failed());
    }
}

//-----------------------------------------------------------------------------

void verifyMultiEncode(const Slime & slime, const SimpleBuffer &expect) {
    size_t cnt = 5;
    std::vector<SimpleBuffer> buffers(cnt);
    BinaryFormat::encode(slime, buffers[0]);
    for (size_t i = 1; i < cnt; ++i) {
        Slime s;
        EXPECT_TRUE(BinaryFormat::decode(buffers[i - 1].get(), s));
        BinaryFormat::encode(s, buffers[i]);
        EXPECT_EQUAL(expect.get().size, buffers[i].get().size);
        EXPECT_EQUAL(slime, s);
    }
}

//-----------------------------------------------------------------------------

namespace {
template <typename T>
void encodeBasic(OutputWriter &out,
                 const typename TypeTraits<T>::PassType &value);

template <>
void encodeBasic<BOOL>(OutputWriter &out, const bool &value)
{
    out.write(encode_type_and_meta(BOOL::ID, value ? 1 : 0));
}

template <> void encodeBasic<LONG>(OutputWriter &out, const int64_t &value)
{
    write_type_and_bytes<false>(out, LONG::ID, encode_zigzag(value));
}

template <> void encodeBasic<DOUBLE>(OutputWriter &out, const double &value)
{
    write_type_and_bytes<true>(out, DOUBLE::ID, encode_double(value));
}

template <> void encodeBasic<STRING>(OutputWriter &out, const Memory &value)
{
    write_type_and_size(out, STRING::ID, value.size);
    out.write(value.data, value.size);
}

template <> void encodeBasic<DATA>(OutputWriter &out, const Memory &value)
{
    write_type_and_size(out, DATA::ID, value.size);
    out.write(value.data, value.size);
}
} // namespace <unnamed>

//-----------------------------------------------------------------------------

template <typename T>
void
setSlimeValue(Slime& slime, const typename TypeTraits<T>::PassType &value);

template <>
void
setSlimeValue<BOOL>(Slime& slime, const TypeTraits<BOOL>::PassType &value) {
    slime.setBool(value);
}
template <>
void
setSlimeValue<LONG>(Slime& slime, const TypeTraits<LONG>::PassType &value) {
    slime.setLong(value);
}
template <>
void
setSlimeValue<DOUBLE>(Slime& slime, const TypeTraits<DOUBLE>::PassType &value) {
    slime.setDouble(value);
}
template <>
void
setSlimeValue<STRING>(Slime& slime, const TypeTraits<STRING>::PassType &value) {
    slime.setString(value);
}
template <>
void
setSlimeValue<DATA>(Slime& slime, const TypeTraits<DATA>::PassType &value) {
    slime.setData(value);
}


template <typename T>
void verifyBasic(const typename TypeTraits<T>::PassType &value) {
    Slime slime;
    setSlimeValue<T>(slime, value);
    SimpleBuffer expect;
    SimpleBuffer actual;
    {
        OutputWriter out(expect, 32);
        write_cmpr_ulong(out, 0); // num symbols
        encodeBasic<T>(out, value);
    }
    BinaryFormat::encode(slime, actual);
    EXPECT_EQUAL(MemCmp(expect.get()), MemCmp(actual.get()));
    TEST_DO(verifyMultiEncode(slime, expect));
}

//-----------------------------------------------------------------------------

TEST("testZigZagConversion") {
    EXPECT_EQUAL(0UL, encode_zigzag(0L));
    EXPECT_EQUAL(0L, decode_zigzag(encode_zigzag(0L)));

    EXPECT_EQUAL(1UL, encode_zigzag(-1L));
    EXPECT_EQUAL(-1L, decode_zigzag(encode_zigzag(-1L)));

    EXPECT_EQUAL(2UL, encode_zigzag(1L));
    EXPECT_EQUAL(1L, decode_zigzag(encode_zigzag(1L)));

    EXPECT_EQUAL(3UL, encode_zigzag(-2L));
    EXPECT_EQUAL(-2L, decode_zigzag(encode_zigzag(-2L)));

    EXPECT_EQUAL(4UL, encode_zigzag(2L));
    EXPECT_EQUAL(2L, decode_zigzag(encode_zigzag(2L)));

    EXPECT_EQUAL(1999UL, encode_zigzag(-1000L));
    EXPECT_EQUAL(-1000L, decode_zigzag(encode_zigzag(-1000L)));

    EXPECT_EQUAL(2000UL, encode_zigzag(1000L));
    EXPECT_EQUAL(1000L, decode_zigzag(encode_zigzag(1000L)));

    EXPECT_EQUAL(0xffffffffffffffffUL,
               encode_zigzag(0x8000000000000000L));
    EXPECT_EQUAL(int64_t(0x8000000000000000L),
               decode_zigzag(encode_zigzag(0x8000000000000000L)));

    EXPECT_EQUAL(0xfffffffffffffffeUL,
               encode_zigzag(0x7fffffffffffffffL));
    EXPECT_EQUAL(0x7fffffffffffffffL,
               decode_zigzag(encode_zigzag(0x7fffffffffffffffL)));
}

TEST("testDoubleConversion") {
    EXPECT_EQUAL(0UL, encode_double(0.0));
    EXPECT_EQUAL(0.0, decode_double(encode_double(0.0)));

    EXPECT_EQUAL(0x8000000000000000UL, encode_double(-0.0));
    EXPECT_EQUAL(-0.0, decode_double(encode_double(-0.0)));

    EXPECT_EQUAL(0x3ff0000000000000UL, encode_double(1.0));
    EXPECT_EQUAL(1.0, decode_double(encode_double(1.0)));

    EXPECT_EQUAL(0xbff0000000000000UL, encode_double(-1.0));
    EXPECT_EQUAL(-1.0, decode_double(encode_double(-1.0)));

    EXPECT_EQUAL(0x4000000000000000UL, encode_double(2.0));
    EXPECT_EQUAL(2.0, decode_double(encode_double(2.0)));

    EXPECT_EQUAL(0xc000000000000000UL, encode_double(-2.0));
    EXPECT_EQUAL(-2.0, decode_double(encode_double(-2.0)));
}

TEST("testTypeAndMetaMangling") {
    for (uint32_t type = 0; type < TYPE_LIMIT; ++type) {
        for (uint32_t meta = 0; meta < META_LIMIT; ++meta) {
            char mangled = encode_type_and_meta(type, meta);
            EXPECT_EQUAL(type, decode_type(mangled));
            EXPECT_EQUAL(meta, decode_meta(mangled));
        }
    }
}

TEST("testCmprUlong") {
    // check min/max values for different byte counts
    for (uint32_t n = 1; n <= MAX_CMPR_SIZE; ++n) {
        TEST_STATE(vespalib::make_string("n = %d", n).c_str());
        uint64_t min = (n == 1) ? 0x00
                       : (1ULL << ((n - 1) * 7));
        uint64_t max = (n == MAX_CMPR_SIZE) ? 0xffffffffffffffff
                       : (1ULL << (n * 7)) - 1;
        SimpleBuffer expect_min;
        SimpleBuffer expect_max;
        for (uint32_t i = 0; i < n; ++i) {
            if (i + 1 < n) {
                expect_min.add(0x80);
                expect_max.add(0xff);
            } else {
                if (n == 1) {
                    expect_min.add(0x00);
                } else {
                    expect_min.add(0x01);
                }
                if (n == MAX_CMPR_SIZE) {
                    expect_max.add(0x01);
                } else {
                    expect_max.add(0x7f);
                }
            }
        }
        TEST_DO(verify_cmpr_ulong(min, expect_min));
        TEST_DO(verify_cmpr_ulong(max, expect_max));
    }
    // check byte order and data preservation
    for (int mul = 1; mul <= 15; ++mul) { // 8(i) * 15(mul) = 120 <= 127 = 0x7f
        TEST_STATE(vespalib::make_string("mul = %d", mul).c_str());
        SimpleBuffer expect;
        uint64_t value = 0;
        for (uint32_t i = 0; i < MAX_CMPR_SIZE - 1; ++i) {
            value |= (uint64_t(i * mul) << (i * 7));
            if (i < MAX_CMPR_SIZE - 2) {
                expect.add(0x80 + (i * mul));
            } else {
                expect.add(i * mul);
            }
        }
        TEST_DO(verify_cmpr_ulong(value, expect));
    }
}

TEST("testTypeAndSize") {
    for (uint32_t type = 0; type < TYPE_LIMIT; ++type) {
        for (uint32_t size = 0; size < 500; ++size) {
            SimpleBuffer expect;
            SimpleBuffer actual;
            {
                OutputWriter expect_out(expect, 32);
                if ((size + 1) < META_LIMIT) {
                    expect_out.write(encode_type_and_meta(type, size + 1));
                } else {
                    expect_out.write(type);
                    write_cmpr_ulong(expect_out, size);
                }
            }
            {
                OutputWriter actual_out(actual, 32);
                write_type_and_size(actual_out, type, size);
            }
            EXPECT_EQUAL(MemCmp(expect.get()), MemCmp(actual.get()));
            {
                InputReader input(expect);
                char byte = input.read();
                uint32_t decodedType = decode_type(byte);
                uint64_t decodedSize = read_size(input, decode_meta(byte));
                EXPECT_EQUAL(type, decodedType);
                EXPECT_EQUAL(size, decodedSize);
                EXPECT_EQUAL(input.get_offset(), actual.get().size);
                EXPECT_TRUE(!input.failed());
            }
        }
    }
}

namespace {

uint64_t build_bits(uint32_t type, uint32_t n, uint32_t pre, bool hi,
                    SimpleBuffer &expect)
{
    uint64_t value = 0;
    expect.add(encode_type_and_meta(type, n));
    for (uint32_t i = 0; i < n; ++i) {
        char byte = (i < pre) ? 0x00 : (0x11 * (i - pre + 1));
        expect.add(byte);
        int shift = hi ? ((7 - i) * 8) : (i * 8);
        value |= ((uint64_t(byte)&0xff) << shift);
    }
    return value;
}

} // namespace <unnamed>

TEST("testTypeAndBytes") {
    for (uint32_t type = 0; type < TYPE_LIMIT; ++type) {
        TEST_STATE(vespalib::make_string("type = %d",
                                              type).c_str());
        for (uint32_t n = 0; n <= MAX_NUM_SIZE; ++n) {
            TEST_STATE(vespalib::make_string("n = %d",
                                                  n).c_str());
            for (uint32_t pre = 0; (pre == 0) || (pre < n); ++pre) {
                TEST_STATE(vespalib::make_string("pre = %d",
                                                      pre).c_str());
                for (int hi = 0; hi < 2; ++hi) {
                    TEST_STATE(vespalib::make_string("hi = %d",
                                                          hi).c_str());
                    SimpleBuffer expect;
                    SimpleBuffer actual;
                    uint64_t bits = build_bits(type, n, pre,
                                               (hi != 0), expect);
                    {
                        OutputWriter out(actual, 32);
                        if (hi != 0) {
                            write_type_and_bytes<true>(out, type, bits);
                        } else {
                            write_type_and_bytes<false>(out, type, bits);
                        }
                    }
                    EXPECT_EQUAL(MemCmp(expect.get()), MemCmp(actual.get()));
                    {
                        InputReader input(expect);
                        uint32_t size = decode_meta(input.read());
                        uint64_t decodedBits;
                        if (hi != 0) {
                            decodedBits = read_bytes<true>(input, size);
                        } else {
                            decodedBits = read_bytes<false>(input, size);
                        }
                        EXPECT_EQUAL(bits, decodedBits);
                        EXPECT_EQUAL(input.get_offset(), actual.get().size);
                        EXPECT_TRUE(!input.failed());
                    }
                }
            }
        }
    }
}

TEST("testEmpty") {
    Slime slime;
    SimpleBuffer expect;
    SimpleBuffer actual;
    {
        OutputWriter out(expect, 32);
        write_cmpr_ulong(out, 0); // num symbols
        out.write(0);       // nix
    }
    BinaryFormat::encode(slime, actual);
    EXPECT_EQUAL(MemCmp(expect.get()), MemCmp(actual.get()));
    TEST_DO(verifyMultiEncode(slime, expect));
}

TEST("testBasic") {
    TEST_DO(verifyBasic<BOOL>(false));
    TEST_DO(verifyBasic<BOOL>(true));

    TEST_DO(verifyBasic<LONG>(0));
    TEST_DO(verifyBasic<LONG>(123));
    TEST_DO(verifyBasic<LONG>(-123));
    TEST_DO(verifyBasic<LONG>(123456));
    TEST_DO(verifyBasic<LONG>(-123456));
    TEST_DO(verifyBasic<LONG>(123456789));
    TEST_DO(verifyBasic<LONG>(-123456789));

    TEST_DO(verifyBasic<DOUBLE>(0.0));
    TEST_DO(verifyBasic<DOUBLE>(2.5));
    TEST_DO(verifyBasic<DOUBLE>(-2.5));
    TEST_DO(verifyBasic<DOUBLE>(-1000.0));
    TEST_DO(verifyBasic<DOUBLE>(1000.0));
    TEST_DO(verifyBasic<DOUBLE>(1.0e32));
    TEST_DO(verifyBasic<DOUBLE>(-1.0e32));
    TEST_DO(verifyBasic<DOUBLE>(1.0e-32));
    TEST_DO(verifyBasic<DOUBLE>(-1.0e-32));

    TEST_DO(verifyBasic<STRING>(Memory("foo")));
    TEST_DO(verifyBasic<STRING>(Memory("bar")));
    EXPECT_EQUAL(500u, std::string(500, 'x').size());
    TEST_DO(verifyBasic<STRING>(Memory(std::string(500, 'x'))));

    TEST_DO(verifyBasic<DATA>(Memory("foo")));
    TEST_DO(verifyBasic<DATA>(Memory("bar")));
    EXPECT_EQUAL(500u, std::string(500, 'x').size());
    TEST_DO(verifyBasic<DATA>(Memory(std::string(500, 'x'))));
}

TEST("testArray") {
    Slime slime;
    SimpleBuffer expect;
    SimpleBuffer actual;
    Cursor &c = slime.setArray();
    c.addNix();
    c.addBool(true);
    c.addLong(5);
    c.addDouble(3.5);
    c.addString(Memory("string"));
    c.addData(Memory("data"));
    {
        OutputWriter out(expect, 32);
        write_cmpr_ulong(out, 0); // num symbols
        write_type_and_size(out, ARRAY::ID, 6);
        out.write(0);
        encodeBasic<BOOL>(out, true);
        encodeBasic<LONG>(out, 5);
        encodeBasic<DOUBLE>(out, 3.5);
        encodeBasic<STRING>(out, Memory("string"));
        encodeBasic<DATA>(out, Memory("data"));
    }
    BinaryFormat::encode(slime, actual);
    EXPECT_EQUAL(MemCmp(expect.get()), MemCmp(actual.get()));
    TEST_DO(verifyMultiEncode(slime, expect));
}

TEST("testObject") {
    Slime slime;
    SimpleBuffer expect;
    SimpleBuffer actual;
    Cursor &c = slime.setObject();
    c.setNix("a");
    c.setBool("b", true);
    c.setLong("c", 5);
    c.setDouble("d", 3.5);
    c.setString("e", Memory("string"));
    c.setData("f", Memory("data"));
    {
        OutputWriter out(expect, 32);
        write_cmpr_ulong(out, 6); // num symbols
        write_cmpr_ulong(out, 1);
        out.write("a", 1); // 0
        write_cmpr_ulong(out, 1);
        out.write("b", 1); // 1
        write_cmpr_ulong(out, 1);
        out.write("c", 1); // 2
        write_cmpr_ulong(out, 1);
        out.write("d", 1); // 3
        write_cmpr_ulong(out, 1);
        out.write("e", 1); // 4
        write_cmpr_ulong(out, 1);
        out.write("f", 1); // 5
        write_type_and_size(out, OBJECT::ID, 6);
        write_cmpr_ulong(out, 0);
        out.write(0);
        write_cmpr_ulong(out, 1);
        encodeBasic<BOOL>(out, true);
        write_cmpr_ulong(out, 2);
        encodeBasic<LONG>(out, 5);
        write_cmpr_ulong(out, 3);
        encodeBasic<DOUBLE>(out, 3.5);
        write_cmpr_ulong(out, 4);
        encodeBasic<STRING>(out, Memory("string"));
        write_cmpr_ulong(out, 5);
        encodeBasic<DATA>(out, Memory("data"));
    }
    BinaryFormat::encode(slime, actual);
    EXPECT_EQUAL(expect.get().size, actual.get().size);
    TEST_DO(verifyMultiEncode(slime, expect));
}

TEST("testNesting") {
    SimpleBuffer expect;
    SimpleBuffer actual;
    Slime slime;
    {
        Cursor &c1 = slime.setObject();
        {
            c1.setLong("bar", 10);
            {
                Cursor &c2 = c1.setArray("foo");
                c2.addLong(20);                 // [0]
                {
                    Cursor &c3 = c2.addObject(); // [1]
                    c3.setLong("answer", 42);
                }
            }
        }
    }
    {
        OutputWriter out(expect, 32);
        write_cmpr_ulong(out, 3);   // num symbols
        write_cmpr_ulong(out, 3);
        out.write("bar", 3);    // 0
        write_cmpr_ulong(out, 3);
        out.write("foo", 3);    // 1
        write_cmpr_ulong(out, 6);
        out.write("answer", 6); // 2
        write_type_and_size(out, OBJECT::ID, 2);
        write_cmpr_ulong(out, 0);   // bar
        encodeBasic<LONG>(out, 10);
        write_cmpr_ulong(out, 1);   // foo
        write_type_and_size(out, ARRAY::ID, 2);
        encodeBasic<LONG>(out, 20);
        write_type_and_size(out, OBJECT::ID, 1);
        write_cmpr_ulong(out, 2);   // answer
        encodeBasic<LONG>(out, 42);
    }
    BinaryFormat::encode(slime, actual);
    EXPECT_EQUAL(expect.get().size, actual.get().size);
    TEST_DO(verifyMultiEncode(slime, expect));
}

TEST("testSymbolReuse") {
    SimpleBuffer expect;
    SimpleBuffer actual;
    Slime slime;
    {
        Cursor &c1 = slime.setArray();
        {
            {
                Cursor &c2 = c1.addObject();
                c2.setLong("foo", 10);
                c2.setLong("bar", 20);
            }
            {
                Cursor &c2 = c1.addObject();
                c2.setLong("foo", 100);
                c2.setLong("bar", 200);
            }
        }
    }
    {
        OutputWriter out(expect, 32);
        write_cmpr_ulong(out, 2);   // num symbols
        write_cmpr_ulong(out, 3);
        out.write("foo", 3); // 0
        write_cmpr_ulong(out, 3);
        out.write("bar", 3); // 1
        write_type_and_size(out, ARRAY::ID, 2);
        write_type_and_size(out, OBJECT::ID, 2);
        write_cmpr_ulong(out, 0);   // foo
        encodeBasic<LONG>(out, 10);
        write_cmpr_ulong(out, 1);   // bar
        encodeBasic<LONG>(out, 20);
        write_type_and_size(out, OBJECT::ID, 2);
        write_cmpr_ulong(out, 0);   // foo
        encodeBasic<LONG>(out, 100);
        write_cmpr_ulong(out, 1);   // bar
        encodeBasic<LONG>(out, 200);
    }
    BinaryFormat::encode(slime, actual);
    EXPECT_EQUAL(expect.get().size, actual.get().size);
    TEST_DO(verifyMultiEncode(slime, expect));
}

TEST("testOptionalDecodeOrder") {
    SimpleBuffer data;
    {
        OutputWriter out(data, 32);
        write_cmpr_ulong(out, 5); // num symbols
        write_cmpr_ulong(out, 1);
        out.write("d", 1); // 0
        write_cmpr_ulong(out, 1);
        out.write("e", 1); // 1
        write_cmpr_ulong(out, 1);
        out.write("f", 1); // 2
        write_cmpr_ulong(out, 1);
        out.write("b", 1); // 3
        write_cmpr_ulong(out, 1);
        out.write("c", 1); // 4
        write_type_and_size(out, OBJECT::ID, 5);
        write_cmpr_ulong(out, 3); // b
        encodeBasic<BOOL>(out, true);
        write_cmpr_ulong(out, 1); // e
        encodeBasic<STRING>(out, Memory("string"));
        write_cmpr_ulong(out, 0); // d
        encodeBasic<DOUBLE>(out, 3.5);
        write_cmpr_ulong(out, 4); // c
        encodeBasic<LONG>(out, 5);
        write_cmpr_ulong(out, 2); // f
        encodeBasic<DATA>(out, Memory("data"));
    }
    Slime slime;
    EXPECT_TRUE(BinaryFormat::decode(data.get(), slime));
    Cursor &c = slime.get();
    EXPECT_TRUE(slime.get().valid());
    EXPECT_EQUAL(OBJECT::ID, slime.get().type().getId());
    EXPECT_EQUAL(5u, c.children());
    EXPECT_EQUAL(true, c["b"].asBool());
    EXPECT_EQUAL(5, c["c"].asLong());
    EXPECT_EQUAL(3.5, c["d"].asDouble());
    EXPECT_EQUAL(std::string("string"), c["e"].asString().make_string());
    EXPECT_EQUAL(std::string("data"), c["f"].asData().make_string());
    EXPECT_TRUE(!c[5].valid()); // not ARRAY
}

Slime from_json(const vespalib::string &json) {
    Slime slime;
    EXPECT_TRUE(vespalib::slime::JsonFormat::decode(json, slime) > 0);
    return slime;
}

TEST("require that decode_into remaps symbols correctly") {
    Slime expect = from_json("{a:1,b:2,c:{b:10,x:20,c:30}}");
    Slime actual = from_json("{a:1,b:2}");
    Slime inner = from_json("{b:10,x:20,c:30}");

    SimpleBuffer buf;
    BinaryFormat::encode(inner, buf);
    BinaryFormat::decode_into(buf.get(), actual, ObjectInserter(actual.get(), "c"));
    EXPECT_EQUAL(expect, actual);
    EXPECT_EQUAL(actual.symbols(), 4u);
}

TEST("require that decode_into without symbol names work") {
    Slime slime;
    Slime inner = from_json("{}");

    Symbol my_sym(42);
    inner.get().setLong(my_sym, 100);

    SimpleBuffer buf;
    BinaryFormat::encode(inner, buf);
    BinaryFormat::decode_into(buf.get(), slime, SlimeInserter(slime));
    EXPECT_EQUAL(slime.symbols(), 0u);
    EXPECT_EQUAL(slime.get()[my_sym].asLong(), 100);
}

TEST("require that decode failure results in 0 return value") {
    SimpleBuffer buf;
    buf.add(char(0)); // empty symbol table, but no value
    Slime slime;
    EXPECT_EQUAL(BinaryFormat::decode(buf.get(), slime), 0u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
