// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*****************************************************************************
* @author Aleksander Øhrn
* @date    Creation date: 2000-10-16
* Utility functions for base-64 encoding/decoding.
*****************************************************************************/

#include "base64.h"
#include <cstdio>
#include <cctype>

static const char _base64[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

int
Fast_Base64::Decode(const char *source, unsigned int length, char *destination) {

    unsigned int i;

    // Sanity checks.
    if (!source || !destination)
        return -1;

    int  state = 0;
    int  index = 0;
    char symbol;

    // Process all source symbols.
    for (i = 0; i < length; ++i) {

        symbol = source[i];

        // Skip whitespace.
        if (isspace(symbol))
            continue;

        // Are we at the end?
        if (symbol == '=')
            break;

        // Lookup symbol in base-64 table.
        if ((symbol >= 'A') && (symbol <= 'Z'))
            symbol = symbol - 'A';
        else if ((symbol >= 'a') && (symbol <= 'z'))
            symbol = symbol - 'a' + 26;
        else if ((symbol >= '0') && (symbol <= '9'))
            symbol = symbol - '0' + 52;
        else if (symbol == '+')
            symbol = 62;
        else if (symbol == '/')
            symbol = 63;
        else
            return -1;

        // Write stuff into the destination buffer.
        switch (state) {
        case 0: destination[index] = symbol << 2;
            state = 1;
            break;
        case 1: destination[index] |= symbol >> 4;
            destination[++index] = (symbol & 0x0f) << 4;
            state = 2;
            break;
        case 2: destination[index] |= symbol >> 2;
            destination[++index] = (symbol & 0x03) << 6;
            state = 3;
            break;
        case 3: destination[index++] |= symbol;
            state = 0;
            break;
        }

    }

    // Did we end on a byte boundary, and/or with erroneous trailing characters?
    if (i < length) {

        // We've got a pad character. Skip it, and get the next symbol.
        symbol = source[++i];

        // Check state.
        switch (state) {
        case 0:
        case 1: return -1;
        case 2: for (; i < length; ++i) {
                symbol = source[i];
                if (!isspace(symbol))
                    break;
            }
            if (symbol != '=' || i == length)
                return -1;
            symbol = source[++i];
            [[fallthrough]];
        case 3: for (; i < length; ++i) {
                symbol = source[i];
                if (symbol == '\0')
                    break;
                if (!isspace(symbol))
                    return -1;
            }
            if (destination[index] != '\0')
                return -1;
        }
    }

    // We ended by seeing the end of the string.  Make sure we have no partial bytes lying around.
    else {
        if (state != 0)
            return -1;
    }

    return index;

}


int
Fast_Base64::Encode(const char *source, unsigned int length, char *destination) {

    unsigned int i;

    // Sanity checks.
    if (!source || !destination)
        return -1;

    char         a, b, c, d;          // Holds encoded bytes.
    const char   *p = source;         // Points into the current location in the source buffer.
    char         *q = destination;    // Points into the current location in the destination buffer.
    unsigned int  n = length / 3;     // Number of 4-byte encodings.
    unsigned int  m = length - 3 * n; // Remainder if length is not divisible by 3.

    // Encode symbols. Three source symbols translates into four destination synbols.
    for (i = 0; i < n; ++i) {
        a = ((p[0] & 0xfc) >> 2);
        b = ((p[0] & 0x03) << 4) | ((p[1] & 0xf0) >> 4);
        c = ((p[1] & 0x0f) << 2) | ((p[2] & 0xc0) >> 6);
        d = p[2] & 0x3f;
        p += 3;
        sprintf(q, "%c%c%c%c",
                _base64[static_cast<int>(a)],
                _base64[static_cast<int>(b)],
                _base64[static_cast<int>(c)],
                _base64[static_cast<int>(d)]);
        q += 4;
    }

    // Handle remaining symbols, if any.
    if (m == 0) {
    }
    else if (m == 1) {
        a = ((p[0] & 0xfc) >> 2);
        b = ((p[0] & 0x03) << 4);
        sprintf(q, "%c%c==",
                _base64[static_cast<int>(a)],
                _base64[static_cast<int>(b)]);
        q += 4;
    }
    else {
        a = ((p[0] & 0xfc) >> 2);
        b = ((p[0] & 0x03) << 4) | ((p[1] & 0xf0) >> 4);
        c = ((p[1] & 0x0f) << 2);
        sprintf(q, "%c%c%c=",
                _base64[static_cast<int>(a)],
                _base64[static_cast<int>(b)],
                _base64[static_cast<int>(c)]);
        q += 4;
    }

    return q - destination + 1;

}
