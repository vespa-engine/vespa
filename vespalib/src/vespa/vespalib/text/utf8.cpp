// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utf8.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.utf8");

namespace vespalib {

void Utf8::throwX(const char *msg, unsigned int number)
{
    vespalib::string what = make_string("%s: \\x%02X", msg, number);
    throw IllegalArgumentException(what);
}

uint32_t Utf8Reader::getComplexChar(unsigned char firstbyte, uint32_t fallback) noexcept
{
    if (_pos == size()) {
        // this shouldn't happen ...
        LOG(warning, "last byte %02X of Utf8Reader block was incomplete UTF-8", firstbyte);
        return fallback;
    }
    assert(hasMore()); // should never fall out of range
    if (! Utf8::validFirstByte(firstbyte)) {
        LOG(debug, "invalid first byte %02X in Utf8Reader data block", firstbyte);
        return fallback;
    }
    int need = Utf8::numContBytes(firstbyte);

    if (_pos + need > size()) {
        LOG(debug, "incomplete data (first byte %02X, pos=%zu, need=%d, size=%zu) in Utf8Reader data block",
            firstbyte, _pos, need, size());
        return fallback;
    }

    if (need == 1) {
        unsigned char contbyte = (*this)[_pos];
        if (Utf8::validContByte(contbyte)) {
            ++_pos;
            uint32_t r = decode2(firstbyte, contbyte);
            // check > 0x7F ?
            return r;
        } else {
            LOG(debug, "invalid continuation byte %02X in Utf8Reader data block", contbyte);
            return fallback;
        }
    }

    if (need == 2) {
        unsigned char contbyte1 = (*this)[_pos];
        unsigned char contbyte2 = (*this)[_pos+1];
        if (Utf8::validContByte(contbyte1) &&
            Utf8::validContByte(contbyte2))
        {
            _pos += 2;
            uint32_t r = decode3(firstbyte, contbyte1, contbyte2);
            if (r >= first_high_surrogate && r <= last_low_surrogate) {
                // surrogates not valid in utf8
                return fallback;
            }
            // check > 0x7FF ?
            return r;
        } else {
            LOG(debug, "invalid continuation bytes %02X/%02X in Utf8Reader data block", contbyte1, contbyte2);
            return fallback;
        }
    }

    unsigned char contbyte1 = (*this)[_pos];
    unsigned char contbyte2 = (*this)[_pos+1];
    unsigned char contbyte3 = (*this)[_pos+2];
    if (Utf8::validContByte(contbyte1) &&
        Utf8::validContByte(contbyte2) &&
        Utf8::validContByte(contbyte3))
    {
        _pos += 3;
        // check > 0xFFFF?
        return decode4(firstbyte, contbyte1, contbyte2, contbyte3);
    } else {
        LOG(debug, "invalid continuation bytes %02X/%02X/%02X in Utf8Reader data block",
            contbyte1, contbyte2, contbyte3);
        return fallback;
    }
}


uint32_t
Utf8ReaderForZTS::getComplexChar(unsigned char firstbyte, uint32_t fallback) noexcept
{
    if (! Utf8::validFirstByte(firstbyte)) {
        LOG(debug, "invalid first byte %02X in Utf8Reader data block", firstbyte);
        return fallback;
    }
    int need = Utf8::numContBytes(firstbyte);

    if (need == 1) {
        if (_p[0] == 0) {
            LOG(debug, "incomplete character (first byte %02X) in Utf8ReaderZTS", firstbyte);
            return fallback;
        }
        unsigned char contbyte = _p[0];
        if (Utf8::validContByte(contbyte)) {
            _p += 1;
            uint32_t r = decode2(firstbyte, contbyte);
            // check > 0x7F ?
            return r;
        } else {
            LOG(debug, "invalid continuation byte %02X in Utf8Reader data block", contbyte);
            return fallback;
        }
    }

    if (need == 2) {
        if (_p[0] == 0 || _p[1] == 0) {
            LOG(debug, "incomplete character (first byte %02X) in Utf8ReaderZTS", firstbyte);
            return fallback;
        }
        unsigned char contbyte1 = _p[0];
        unsigned char contbyte2 = _p[1];
        if (Utf8::validContByte(contbyte1) &&
            Utf8::validContByte(contbyte2))
        {
            _p += 2;
            uint32_t r = decode3(firstbyte, contbyte1, contbyte2);
            if (r >= first_high_surrogate && r <= last_low_surrogate) {
                // surrogates not valid in utf8
                return fallback;
            }
            // check > 0x7FF ?
            return r;
        } else {
            LOG(debug, "invalid continuation bytes %02X/%02X in Utf8Reader data block", contbyte1, contbyte2);
            return fallback;
        }
    }

    if (_p[0] == 0 || _p[1] == 0 || _p[2] == 0) {
        LOG(debug, "incomplete character (first byte %02X) in Utf8ReaderZTS", firstbyte);
        return fallback;
    }
    unsigned char contbyte1 = _p[0];
    unsigned char contbyte2 = _p[1];
    unsigned char contbyte3 = _p[2];
    if (Utf8::validContByte(contbyte1) &&
        Utf8::validContByte(contbyte2) &&
        Utf8::validContByte(contbyte3))
    {
        _p += 3;
        // check > 0xFFFF?
        return decode4(firstbyte, contbyte1, contbyte2, contbyte3);
    } else {
        LOG(debug, "invalid continuation bytes %02X/%02X/%02X in Utf8Reader data block", contbyte1, contbyte2, contbyte3);
        return fallback;
    }
}


template <typename Target>
Utf8Writer<Target>&
Utf8Writer<Target>::putChar(uint32_t codepoint)
{
    if (codepoint < 0x80) {
        _target.push_back((char)codepoint);
    } else if (codepoint < 0x800) {
        char low6 = (codepoint & low_6bits_mask);
        low6 |= 0x80;
        codepoint >>= 6;
        char first5 = codepoint;
        first5 |= 0xC0;
        _target.push_back(first5);
        _target.push_back(low6);
    } else if (codepoint < 0x10000) {
        char low6 = (codepoint & low_6bits_mask);
        low6 |= 0x80;

        codepoint >>= 6;
        char mid6 = (codepoint & low_6bits_mask);
        mid6 |= 0x80;

        codepoint >>= 6;
        char first4 = codepoint;
        first4 |= 0xE0;

        _target.push_back(first4);
        _target.push_back(mid6);
        _target.push_back(low6);
    } else if (codepoint < 0x110000) {
        char low6 = (codepoint & low_6bits_mask);
        low6 |= 0x80;

        codepoint >>= 6;
        char mid6 = (codepoint & low_6bits_mask);
        mid6 |= 0x80;

        codepoint >>= 6;
        char hi6 = (codepoint & low_6bits_mask);
        hi6 |= 0x80;

        codepoint >>= 6;
        char first3 = codepoint;
        first3 |= 0xF0;

        _target.push_back(first3);
        _target.push_back(hi6);
        _target.push_back(mid6);
        _target.push_back(low6);
    } else {
        Utf8::throwX("invalid ucs4 codepoint", codepoint);
    }
    return *this;
}

template class Utf8Writer<vespalib::string>;
template class Utf8Writer<std::string>;

template <typename T>
T Utf8::filter_invalid_sequences(const T& input) noexcept
{
    T retval;
    Utf8Reader reader(input.c_str(), input.size());
    Utf8Writer writer(retval);
    while (reader.hasMore()) {
        uint32_t ch = reader.getChar();
        writer.putChar(ch);
    }
    return retval;
}

template vespalib::string Utf8::filter_invalid_sequences(const vespalib::string&);
template std::string Utf8::filter_invalid_sequences(const std::string&);

} // namespace
