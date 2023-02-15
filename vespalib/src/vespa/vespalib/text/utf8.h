// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// $Id$

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/**
 * @brief Common utilities for reading and writing UTF-8 data
 **/
class Utf8
{
public:
    enum {
        /// random invalid codepoint
        BAD = 0xBadBad,

        /// unicode standard replacement
        REPLACEMENT_CHAR = 0xFFFD,

        /// old fastlib "bad utf8" replacement
        FASTLIB_BadUTF8Char = 0xfffffffeU,

        /// old fastlib "EOF" escape char
        FASTLIB_EOF = 0xffffffffU
    };

    /**
     * Filter a string (std::string or vespalib::string)
     * and replace any invalid UTF8 sequences with the
     * standard replacement char U+FFFD; note that any
     * UTF-8 encoded surrogates are also considered invalid.
     **/
    template <typename T>
    static T filter_invalid_sequences(const T& input);

    /**
     * check if a byte is valid as the first byte of an UTF-8 character.
     * @param c the byte to be checked
     * @return true if a valid UTF-8 character can start with this byte
     **/
    static bool validFirstByte(unsigned char c) {
        return (c < 0x80 ||
                (c > 0xC1 && c < 0xF5));
    }

    /**
     * return the number of continuation bytes needed to complete
     * an UTF-8 character starting with the given first byte.
     * @param c the first byte (must pass validFirstByte check)
     * @return 0, 1, 2, or 3
     **/
    static int numContBytes(unsigned char c) {
        if (c < 0x80) return 0;
        if (c > 0xC1 && c < 0xE0) return 1;
        if (c > 0xDF && c < 0xF0) return 2;
        if (c > 0xEF && c < 0xF5) return 3;
        throwX("invalid first byte of UTF8 sequence", c);
    }

    /**
     * check if a byte is valid as a non-first byte of an UTF-8 character.
     * @param c the byte to be checked
     * @return true if a valid UTF-8 character can contain this byte
     **/
    static bool validContByte(unsigned char c) {
        return (c > 0x7F && c < 0xC0);
    }


    /**
     * decode a 2-byte UTF-8 character.  NOTE: assumes that checks
     * are already done, so the following should hold:
     *    validFirstByte(firstbyte) == true
     *    numContBytes(firstbyte) == 1
     *    validContByte(contbyte) == true
     * Also, there is no check for non-normalized UTF-8 here.
     *
     * @param firstbyte first byte in this UTF-8 character
     * @param contbyte second byte in this UTF-8 character
     * @return decoded UCS-4 codepoint in range [0, 0x7FF]
     **/
    static uint32_t decode2(unsigned char firstbyte,
                            unsigned char contbyte)
    {
        uint32_t r = (firstbyte & low_5bits_mask);
        r <<= 6;
        r |= (contbyte & low_6bits_mask);
        return r;
    }


    /**
     * decode a 3-byte UTF-8 character.  NOTE: assumes that checks
     * are already done, so the following should hold:
     *    validFirstByte(firstbyte) == true
     *    numContBytes(firstbyte) == 2
     *    validContByte(contbyte1) == true
     *    validContByte(contbyte2) == true
     * Also, there is no check for non-normalized UTF-8 here.
     *
     * @param firstbyte first byte in this UTF-8 character
     * @param contbyte1 second byte in this UTF-8 character
     * @param contbyte2 third byte in this UTF-8 character
     * @return decoded UCS-4 codepoint in range [0, 0xFFFF]
     **/
    static uint32_t decode3(unsigned char firstbyte,
                            unsigned char contbyte1,
                            unsigned char contbyte2)
    {
        uint32_t r = (firstbyte & low_4bits_mask);
        r <<= 6;
        r |= (contbyte1 & low_6bits_mask);
        r <<= 6;
        r |= (contbyte2 & low_6bits_mask);
        return r;
    }


    /**
     * decode a 4-byte UTF-8 character.  NOTE: assumes that checks
     * are already done, so the following should hold:
     *    validFirstByte(firstbyte) == true
     *    numContBytes(firstbyte) == 3
     *    validContByte(contbyte1) == true
     *    validContByte(contbyte2) == true
     *    validContByte(contbyte3) == true
     * Also, there is no check for non-normalized UTF-8 here.
     *
     * @param firstbyte first byte in this UTF-8 character
     * @param contbyte1 second byte in this UTF-8 character
     * @param contbyte2 third byte in this UTF-8 character
     * @param contbyte3 fourth byte in this UTF-8 character
     * @return decoded UCS-4 codepoint in range [0, 0x1FFFFF]
     **/
    static uint32_t decode4(unsigned char firstbyte,
                            unsigned char contbyte1,
                            unsigned char contbyte2,
                            unsigned char contbyte3)
    {
        uint32_t r = (firstbyte & low_3bits_mask);
        r <<= 6;
        r |= (contbyte1 & low_6bits_mask);
        r <<= 6;
        r |= (contbyte2 & low_6bits_mask);
        r <<= 6;
        r |= (contbyte3 & low_6bits_mask);
        return r;
    }

protected:

    [[noreturn]] static void throwX(const char *msg, unsigned int number);

    enum {
        low_7bits_mask = 0x7F,
        low_6bits_mask = 0x3F,
        low_5bits_mask = 0x1F,
        low_4bits_mask = 0x0F,
        low_3bits_mask = 0x07,
        first_high_surrogate = 0xD800,
        last_high_surrogate = 0xDBFF,
        first_low_surrogate = 0xDC00,
        last_low_surrogate = 0xDFFF
    };
};


/**
 * @brief Reader class that wraps a block of data to get UTF-8 characters from
 **/
class Utf8Reader
    : public Utf8, private stringref
{
private:
    size_type _pos;

    uint32_t getComplexChar(unsigned char firstbyte, uint32_t fallback);
public:

    /**
     * Construct a reader for the given block of data
     * @param input data to read UTF-8 from (can be read-only)
     **/
    Utf8Reader(stringref input)
        : stringref(input), _pos(0)
    {}

    /**
     * Construct a reader for the given block of data
     * @param start pointer to the start of the block
     * @param sz size of the block in bytes
     **/
    Utf8Reader(const char *start, size_t sz)
        : stringref(start, sz), _pos(0)
    {}

    /**
     * check if the buffer has more data.
     * @return true if there is more data
     **/
    bool hasMore() const { return _pos < size(); }

    /**
     * Decode the UTF-8 character at the current position.
     * NOTE: for performance reasons this won't check
     * that there is more data available, but just assumes
     * that hasMore() would return true.
     * @param fallback the value to return if invalid UTF-8 is found
     * @return a valid UCS-4 codepoint (or the fallback value)
     **/
    uint32_t getChar(uint32_t fallback) {
        unsigned char firstbyte = (*this)[_pos++]; // always steps at least 1 position
        if (firstbyte < 0x80) {
            return firstbyte;
        } else {
            return getComplexChar(firstbyte, fallback);
        }
    }

    /**
     * Decode the UTF-8 character at the current position.
     *
     * NOTE: for performance reasons this won't check
     * that there is more data available, but just assumes
     * that hasMore() would return true.  If invalid UTF-8
     * is found, returns the Unicode REPLACEMENT CHARACTER, see
     * http://en.wikipedia.org/wiki/Specials_%28Unicode_block%29#Replacement_character
     * for more details.
     *
     * @return a valid UCS-4 codepoint
     **/
    uint32_t getChar() { return getChar(Utf8::REPLACEMENT_CHAR); }

    /**
     * obtain the current byte offset position
     * @return position in bytes
     **/
    size_type getPos() const { return _pos; }
};


/**
 * @brief Reader class that wraps a zero-terminated string it gets UTF-8 characters from
 *
 * If at all possible, rewrite your code to use Utf8Reader instead.
 **/
class Utf8ReaderForZTS
    : public Utf8
{
private:
    const char * &_p;
    uint32_t getComplexChar(unsigned char firstbyte, uint32_t fallback);
public:

    /**
     * Construct a reader for the given block of data.
     *
     * NOTE: the pointer argument is taken by reference
     * and will be modified in-place, stepping forward
     * for each character you read until it reaches
     * the zero termination.
     *
     * @param start pointer to the start of the block
     **/
    Utf8ReaderForZTS(const char * &start)
        : _p(start)
    {}

    /**
     * check if the buffer has more data.
     * @return true if there is more data
     **/
    bool hasMore() const {
        return (*_p) != '\0';
    }

    /**
     * Decode the UTF-8 character at the current position.
     * NOTE: for performance reasons this won't check
     * that there is more data available, but just assumes
     * that hasMore() would return true.
     * @param fallback the value to return if invalid UTF-8 is found
     * @return a valid UCS-4 codepoint (or the fallback value)
     **/
    uint32_t getChar(uint32_t fallback) {
        unsigned char firstbyte = *_p++; // always steps at least 1 position
        if (firstbyte < 0x80) {
            return firstbyte;
        } else {
            return getComplexChar(firstbyte, fallback);
        }
    }

    /**
     * Decode the UTF-8 character at the current position.
     *
     * NOTE: for performance reasons this won't check
     * that there is more data available, but just assumes
     * that hasMore() would return true.  If invalid UTF-8
     * is found, returns the Unicode REPLACEMENT CHARACTER, see
     * http://en.wikipedia.org/wiki/Specials_%28Unicode_block%29#Replacement_character
     * for more details.
     *
     * @return a valid UCS-4 codepoint
     **/
    uint32_t getChar() { return getChar(Utf8::REPLACEMENT_CHAR); }

    /**
     * count the number of UCS-4 characters will be returned when
     * reading UTF-8 from the given zero-terminated string; like
     * "strlen" does not count the zero termination, but bytes
     * that aren't valid UTF-8 will count as one character each.
     **/
    static size_t countChars(const char *p) {
        Utf8ReaderForZTS reader(p);
        size_t i;
        for (i = 0; reader.hasMore(); ++i) {
            reader.getChar();
        }
        return i;
    }

};


/**
 * @brief Writer class that appends UTF-8 characters to a string
 **/
template <typename Target>
class Utf8Writer : public Utf8
{
    Target &_target;
public:
    /**
     * construct a writer appending to the given string
     * @param target a reference to a vespalib::string
     * that the writer will append to.  Must be writable
     * and must be kept alive while the writer is active.
     **/
    Utf8Writer(Target &target) : _target(target) {}

    /**
     * append the given character to the target string.
     * @param codepoint valid UCS-4 codepoint
     **/
    Utf8Writer& putChar(uint32_t codepoint);
};


}  // namespace vespalib

