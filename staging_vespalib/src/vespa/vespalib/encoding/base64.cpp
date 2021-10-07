// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 *
 * $Id$
 *  This class convert a string to a base64 representation of the string.
 *
 */

#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

namespace vespalib {

static const char base64Chars[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                                  "abcdefghijklmnopqrstuvwxyz"
                                  "0123456789+/=";

// Set -1 for illegal chars that will cause an error.
// Set -2 for illegal chars that will be ignored. (whitespace " \r\t\f\n")
static const signed char base64Backwards[] = {
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -2,  -2,  -1,  -2,  -2,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
  -2,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  62,  -1,  -1,  -1,  63,
  52,  53,  54,  55,  56,  57,  58,  59,  60,  61,  -1,  -1,  -1,  -1,  -1,  -1,
  -1,   0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13,  14,
  15,  16,  17,  18,  19,  20,  21,  22,  23,  24,  25,  -1,  -1,  -1,  -1,  -1,
  -1,  26,  27,  28,  29,  30,  31,  32,  33,  34,  35,  36,  37,  38,  39,  40,
  41,  42,  43,  44,  45,  46,  47,  48,  49,  50,  51,  -1,  -1,  -1,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,
  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1,  -1 };

std::string
Base64::encode(const char* source, int len)
{
        // Assign a string that we know is long enough
    std::string result(getMaximumEncodeLength(len), '\0');
    int outlen = encode(source, len, &result[0], result.size());
    assert(outlen >= 0); // Make sure buffer was big enough.
    result.resize(outlen);
    return result;
}

std::string
Base64::decode(const char* source, int len)
{
    std::string result(getMaximumDecodeLength(len), '\0');
    int outlen = decode(source, len, &result[0], len);
    assert(outlen >= 0);
    result.resize(outlen);
    return result;
}

int
Base64::encode(const char *inBuffer, int inLen, char *outBuffer, int outBufLen)
{
    int i;
    int outLen = 0;
    for (i = 0; inLen >= 3; inLen -= 3) {
        if (outBufLen - outLen < 4) {
            return -1;
        }
        // Do this to keep chars > 127
        unsigned char a = inBuffer[i];
        unsigned char b = inBuffer[i+1];
        unsigned char c = inBuffer[i+2];
        i += 3;

        outBuffer[outLen    ] = base64Chars[ a >> 2 ];
        outBuffer[outLen + 1] = base64Chars[ (a << 4 & 0x30) | (b >> 4) ];
        outBuffer[outLen + 2] = base64Chars[ (b << 2 & 0x3c) | (c >> 6) ];
        outBuffer[outLen + 3] = base64Chars[ c & 0x3f  ];

        outLen += 4;
    }

    if (inLen) {
        if (outBufLen - outLen < 4) {
            return -1;
        }
        // Do this to keep chars with value>127
        unsigned char a = inBuffer[i];

        outBuffer[outLen] = base64Chars[ a >> 2 ];

        if (inLen == 1) {
            outBuffer[outLen + 1] = base64Chars[ (a << 4 & 0x30) ];
            outBuffer[outLen + 2] = '=';
        } else {
            unsigned char b = inBuffer[i + 1];
            outBuffer[outLen + 1] = base64Chars[ (a << 4 & 0x30) | (b >> 4) ];
            outBuffer[outLen + 2] = base64Chars[ b << 2 & 0x3c ];
        }

        outBuffer[outLen + 3] = '=';

        outLen += 4;
    }

    if (outLen >= outBufLen)
        return -1;

    outBuffer[outLen] = '\0';

    return outLen;
}

int
Base64::decode(const char* inBuffer, int inLen, char* outBuffer, int outLen)
{
    // Read char by char to better support skipping of illegal character.
    int readbytes = 0;
    int num_valid_chars = 0;
    const char* thischar = inBuffer;
    signed char curchar;
    int curOut = 0;
    char tmp = 0;

    while( (readbytes++ < inLen) && (*thischar != '\0') && (*thischar != '=')) {
        curchar = base64Backwards[ (unsigned int)(*thischar++) ];

        if (curchar == -2) {
            continue; // Some illegal chars will be skipped.
        } else if (curchar == -1) {
            // Other illegal characters will generate failure
            throw IllegalArgumentException(make_string("Illegal base64 character %u found.",
                                                       (unsigned int) *thischar), VESPA_STRLOC);
        } else {

            // Four bytes from input (eqals three bytes in output)
            if (outLen <= curOut) {
                return -1;
            }
            switch( num_valid_chars % 4 ) {
            case 0:
                tmp = (curchar << 2);
                break;
            case 1:
                outBuffer[curOut++] = tmp |  ((curchar >> 4) & 0x03);
                tmp = ((curchar & 0xf) << 4);
                break;
            case 2:
                outBuffer[curOut++] = tmp | ((curchar >> 2) & 0x0f);
                tmp = ((curchar & 0x03 ) << 6);
                break;
            case 3:
                outBuffer[curOut++] = tmp | curchar;
                tmp = 0;
                break;
            }
            num_valid_chars++;
        }
    }
    return curOut;
}

} // namespace vespalib
