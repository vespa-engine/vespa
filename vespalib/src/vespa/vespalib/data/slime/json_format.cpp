// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "strfmt.h"
#include "json_format.h"
#include "inserter.h"
#include "slime.h"
#include <vespa/vespalib/data/memory_input.h>
#include <vespa/vespalib/locale/c.h>
#include <cmath>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.data.slime.json_format");

namespace vespalib::slime {

namespace {

template <bool COMPACT>
struct JsonEncoder : public ArrayTraverser,
                     public ObjectTraverser
{
    OutputWriter &out;
    int level;
    bool head;

    JsonEncoder(OutputWriter &out_in)
        : out(out_in), level(0), head(true) {}

    void openScope(char c) {
        out.write(c);
        ++level;
        head = true;
    }

    void separate(bool useComma) {
        if (!head && useComma) {
            out.write(',');
        } else {
            head = false;
        }
        if (!COMPACT) {
            out.printf("\n%*s", level * 4, "");
        }
    }

    void closeScope(char c) {
        --level;
        separate(false);
        out.write(c);
    }

    void encodeNIX() {
        out.write("null", 4);
    }
    void encodeBOOL(bool value) {
        if (value) {
            out.write("true", 4);
        } else {
            out.write("false", 5);
        }
    }
    void encodeLONG(int64_t value) {
        out.printf("%ld", value);
    }
    void encodeDOUBLE(double value) {
        if (std::isnan(value) || std::isinf(value)) {
            out.write("null", 4);
        } else {
            out.printf("%g", value);
        }
    }
    void encodeSTRING(const Memory &memory) {
        const char *hex = "0123456789ABCDEF";
        char *p = out.reserve(memory.size * 6 + 2);
        size_t len = 2;
        *p++ = '"';
        const char *pos = memory.data;
        const char *end = memory.data + memory.size;
        for (; pos < end; ++pos) {
            uint8_t c = *pos;
            switch(c) {
            case '"':  *p++ = '\\'; *p++ = '"';  len += 2; break;
            case '\\': *p++ = '\\'; *p++ = '\\'; len += 2; break;
            case '\b': *p++ = '\\'; *p++ = 'b';  len += 2; break;
            case '\f': *p++ = '\\'; *p++ = 'f';  len += 2; break;
            case '\n': *p++ = '\\'; *p++ = 'n';  len += 2; break;
            case '\r': *p++ = '\\'; *p++ = 'r';  len += 2; break;
            case '\t': *p++ = '\\'; *p++ = 't';  len += 2; break;
            default:
                if (c > 0x1f) {
                    *p++ = c; ++len;
                } else { // requires escaping according to RFC 4627
                    *p++ = '\\'; *p++ = 'u'; *p++ = '0'; *p++ = '0';
                    *p++ = hex[(c >> 4) & 0xf]; *p++ = hex[c & 0xf];
                    len += 6;
                }
            }
        }
        *p = '"';
        out.commit(len);
    }
    void encodeDATA(const Memory &memory) {
        const char *hex = "0123456789ABCDEF";
        size_t len = memory.size * 2 + 4;
        char *p = out.reserve(len);
        *p++ = '"'; *p++ = '0'; *p++ = 'x';
        const char *pos = memory.data;
        const char *end = memory.data + memory.size;
        for (; pos < end; ++pos) {
            *p++ = hex[(*pos >> 4) & 0xf]; *p++ = hex[*pos & 0xf];
        }
        *p = '"';
        out.commit(len);
    }
    void encodeARRAY(const Inspector &inspector) {
        ArrayTraverser &array_traverser = *this;
        openScope('[');
        inspector.traverse(array_traverser);
        closeScope(']');
    }
    void encodeOBJECT(const Inspector &inspector) {
        ObjectTraverser &object_traverser = *this;
        openScope('{');
        inspector.traverse(object_traverser);
        closeScope('}');
    }
    void encodeValue(const Inspector &inspector) {
        switch (inspector.type().getId()) {
        case NIX::ID:    return encodeNIX();
        case BOOL::ID:   return encodeBOOL(inspector.asBool());
        case LONG::ID:   return encodeLONG(inspector.asLong());
        case DOUBLE::ID: return encodeDOUBLE(inspector.asDouble());
        case STRING::ID: return encodeSTRING(inspector.asString());
        case DATA::ID:   return encodeDATA(inspector.asData());
        case ARRAY::ID:  return encodeARRAY(inspector);
        case OBJECT::ID: return encodeOBJECT(inspector);
        }
        LOG_ABORT("should not be reached"); // should not be reached
    }
    void entry(size_t idx, const Inspector &inspector) override;
    void field(const Memory &symbol_name, const Inspector &inspector) override;

    static void encode(const Inspector &inspector, OutputWriter &out) {
        JsonEncoder<COMPACT> encoder(out);
        encoder.encodeValue(inspector);
        if (!COMPACT) {
            out.write('\n');
        }
    }
};

template <bool COMPACT>
void
JsonEncoder<COMPACT>::entry(size_t, const Inspector &inspector)
{
    separate(true);
    encodeValue(inspector);
}

template <bool COMPACT>
void
JsonEncoder<COMPACT>::field(const Memory &symbol_name, const Inspector &inspector)
{
    separate(true);
    encodeSTRING(symbol_name);
    if (COMPACT) {
        out.write(':');
    } else {
        out.write(": ", 2);
    }
    encodeValue(inspector);
}

//-----------------------------------------------------------------------------

struct JsonDecoder {
    InputReader &in;
    char c;
    vespalib::string key;
    vespalib::string value;

    JsonDecoder(InputReader &reader) : in(reader), c(in.read()), key(), value() {}

    void next() {
        c = in.try_read();
    }

    bool skip(char x) {
        if (c != x) {
            return false;
        }
        next();
        return true;
    }

    void expect(const char *str) {
        while (*str != 0 && skip(*str)) {
            ++str;
        }
        if (*str != 0) {
            in.fail("unexpected character");
        }
    }

    void skipWhiteSpace() {
        for (;;) {
            switch (c) {
            case ' ': case '\t': case '\n': case '\r':
                next();
                break;
            default: return;
            }
        }
    }

    uint32_t readHexValue(uint32_t len);
    uint32_t dequoteUtf16();
    void readString(vespalib::string &str);
    void readKey();
    void decodeString(Inserter &inserter);
    void decodeObject(Inserter &inserter);
    void decodeArray(Inserter &inserter);
    void decodeNumber(Inserter &inserter);
    void decodeValue(Inserter &inserter) {
        skipWhiteSpace();
        switch (c) {
        case '"': case '\'': return decodeString(inserter);
        case '{': return decodeObject(inserter);
        case '[': return decodeArray(inserter);
        case 't': expect("true"); inserter.insertBool(true); return;
        case 'f': expect("false"); inserter.insertBool(false); return;
        case 'n': expect("null"); inserter.insertNix(); return;
        case '-': case '0': case '1': case '2': case '3': case '4': case '5':
        case '6': case '7': case '8': case '9': return decodeNumber(inserter);
        }
        in.fail("invalid initial character for value");
    }

    void decodeValue(Slime &slime) {
        SlimeInserter inserter(slime);
        decodeValue(inserter);
    }
};

uint32_t
JsonDecoder::readHexValue(uint32_t len)
{
    uint32_t ret = 0;
    for (uint32_t i = 0; i < len; ++i) {
        switch (c) {
        case '0': ret = (ret << 4) | 0; break;
        case '1': ret = (ret << 4) | 1; break;
        case '2': ret = (ret << 4) | 2; break;
        case '3': ret = (ret << 4) | 3; break;
        case '4': ret = (ret << 4) | 4; break;
        case '5': ret = (ret << 4) | 5; break;
        case '6': ret = (ret << 4) | 6; break;
        case '7': ret = (ret << 4) | 7; break;
        case '8': ret = (ret << 4) | 8; break;
        case '9': ret = (ret << 4) | 9; break;
        case 'a': case 'A': ret = (ret << 4) | 0xa; break;
        case 'b': case 'B': ret = (ret << 4) | 0xb; break;
        case 'c': case 'C': ret = (ret << 4) | 0xc; break;
        case 'd': case 'D': ret = (ret << 4) | 0xd; break;
        case 'e': case 'E': ret = (ret << 4) | 0xe; break;
        case 'f': case 'F': ret = (ret << 4) | 0xf; break;
        default:
            in.fail("invalid hex character");
            return 0;
        }
        next();
    }
    return ret;
}

uint32_t
JsonDecoder::dequoteUtf16()
{
    expect("u");
    uint32_t codepoint = readHexValue(4);
    if (codepoint >= 0xd800) {
        if (codepoint < 0xdc00) { // high
            expect("\\u");
            uint32_t low = readHexValue(4);
            if (low >= 0xdc00 && low < 0xe000) {
                codepoint = 0x10000 + ((codepoint - 0xd800) << 10) + (low - 0xdc00);
            } else {
                in.fail("missing low surrogate");
            }
        } else if (codepoint < 0xe000) { // low
            in.fail("unexpected low surrogate");
        }
    }
    return codepoint;
}

void writeUtf8(uint32_t codepoint, vespalib::string &str, uint32_t mask = 0xffffff80) {
    if ((codepoint & mask) == 0) {
        str.push_back((mask << 1) | codepoint);
    } else {
        writeUtf8(codepoint >> 6, str, mask >> (2 - ((mask >> 6) & 0x1)));
        str.push_back(0x80 | (codepoint & 0x3f));
    }
}

void
JsonDecoder::readString(vespalib::string &str)
{
    str.clear();
    char quote = c;
    assert(quote == '"' || quote == '\'');
    next();
    for (;;) {
        switch (c) {
        case '\\':
            next();
            switch (c) {
            case '"': case '\\': case '/': case '\'':
                str.push_back(c);
                break;
            case 'b': str.push_back('\b'); break;
            case 'f': str.push_back('\f'); break;
            case 'n': str.push_back('\n'); break;
            case 'r': str.push_back('\r'); break;
            case 't': str.push_back('\t'); break;
            case 'u': writeUtf8(dequoteUtf16(), str); continue;
            default:
                in.fail(strfmt("invalid quoted char(%02X)", c));
                break;
            }
            next();
            break;
        case '"': case '\'':
            if (c == quote) {
                next();
                return;
            } else {
                str.push_back(c);
                next();
            }
            break;
        case '\0':
            in.fail("unterminated string");
            return;
        default:
            str.push_back(c);
            next();
            break;
        }
    }
}

void
JsonDecoder::readKey() {
    switch (c) {
    case '"': case '\'': return readString(key);
    default:
        key.clear();
        for (;;) {
            switch (c) {
            case ':': case ' ': case '\t': case '\n': case '\r': case '\0': return;
            default:
                key.push_back(c);
                next();
                break;
            }
        }
    }
}

void
JsonDecoder::decodeString(Inserter &inserter)
{
    readString(value);
    inserter.insertString(value);
}

void
JsonDecoder::decodeObject(Inserter &inserter)
{
    Cursor &cursor = inserter.insertObject();
    expect("{");
    skipWhiteSpace();
    if (c != '}') {
        do {
            skipWhiteSpace();
            readKey();
            skipWhiteSpace();
            expect(":");
            ObjectInserter childInserter(cursor, key);
            decodeValue(childInserter);
            skipWhiteSpace();
        } while (skip(','));
    }
    expect("}");
}

void
JsonDecoder::decodeArray(Inserter &inserter)
{
    Cursor &cursor = inserter.insertArray();
    ArrayInserter childInserter(cursor);
    expect("[");
    skipWhiteSpace();
    if (c != ']') {
        do {
            decodeValue(childInserter);
            skipWhiteSpace();
        } while (skip(','));
    }
    expect("]");
}

static int insertNumber(Inserter &inserter, bool isLong, const vespalib::string &value, char **endp);

void
JsonDecoder::decodeNumber(Inserter &inserter)
{
    bool isLong = true;
    value.clear();
    value.push_back(c);
    next();
    for (;;) {
        switch (c) {
        case '+': case '-': case '.': case 'e': case 'E':
            isLong = false;
            [[fallthrough]];
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
            value.push_back(c);
            next();
            break;
        default:
            char *endp;
            int errorCode = insertNumber(inserter, isLong, value, &endp);
            if (errorCode != 0) {
                std::stringstream ss;
                ss << "error inserting number " << value << ". error code: " << errorCode << ". endp - value: " << (endp - value.c_str());
                in.fail(ss.str());
            }
            return;
        }
    }
}

int
insertNumber(Inserter &inserter, bool isLong, const vespalib::string & value, char **endp)
{
    int errorCode = 0;
    errno = 0;
    if (isLong) {
        long val = strtol(value.c_str(), endp, 0);
        errorCode = errno;
        inserter.insertLong(val);
    } else {
        double val = locale::c::strtod(value.c_str(), endp);
        errorCode = errno;
        inserter.insertDouble(val);
    }
    assert(errorCode == 0 || errorCode == ERANGE || errorCode == EINVAL);
    return errorCode;
}

} // namespace vespalib::slime::<unnamed>

void
JsonFormat::encode(const Inspector &inspector, Output &output, bool compact)
{
    size_t chunk_size = 8000;
    OutputWriter out(output, chunk_size);
    if (compact) {
        JsonEncoder<true>::encode(inspector, out);
    } else {
        JsonEncoder<false>::encode(inspector, out);
    }
}

void
JsonFormat::encode(const Slime &slime, Output &output, bool compact)
{
    encode(slime.get(), output, compact);
}

size_t
JsonFormat::decode(Input &input, Slime &slime)
{
    InputReader reader(input);
    JsonDecoder decoder(reader);
    decoder.decodeValue(slime);
    reader.try_unread();
    if (reader.failed()) {
        slime.wrap("partial_result");
        slime.get().setLong("offending_offset", reader.get_offset());
        slime.get().setString("error_message", reader.get_error_message());
    }
    return reader.failed() ? 0 : reader.get_offset();
}

size_t
JsonFormat::decode(const Memory &memory, Slime &slime)
{
    MemoryInput input(memory);
    return decode(input, slime);
}

} // namespace vespalib::slime
