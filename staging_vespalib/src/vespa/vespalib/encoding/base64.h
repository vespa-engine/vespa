// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vespalib::Base64
 * @group util
 *
 * Utility class for conversion between binary and base64 encoded data.
 *
 * $Id$
 */

#pragma once

#include <string>
#include <algorithm>

namespace vespalib {

struct Base64 {

    /**
     * @param sourcelen The length of the source string.
     *
     * @return The maximum number of characters needed to encode a string of
     *         given length, including terminating '\0' byte.
     *
     * @todo This seems to be more than needed. Inspect what encode() does and
     *       make this the exact size.
     **/
    static int getMaximumEncodeLength(int sourcelen)
        { return std::max(6, 2 * sourcelen + 2); }

    /**
     * @param The length of the base64 encoded string to decode.
     *
     * @return The maximum size of the decoded data of a base64 string of the
     *         given length.
     *
     * @todo This seems to be more than needed. Inspect what decode() does and
     *       make this the exact size.
     **/
    static int getMaximumDecodeLength(int sourcelen)
        { return sourcelen; }

    /**
     * Encodes a string of binary data to base 64.
     *
     * @param source    The buffer to convert.
     * @param len       The length of the buffer.
     *
     * @return The base64 encoded string.
     */
    static std::string encode(const std::string& source)
        { return encode(source.c_str(), source.size()); }

    /**
     * Encodes binary data to base 64.
     *
     * @param source    The buffer to convert.
     * @param len       The length of the buffer.
     *
     * @return The base64 encoded string.
     */
    static std::string encode(const char* source, int len);

    /**
     * Encodes binary data pointed to by source, to base 64 data
     * written into dest.
     *
     * @param source    The input buffer.
     * @param sourcelen The length of the input buffer.
     * @param dest      The buffer to write the encoded data to.
     * @param destlen   The length of the output buffer. This may need to be
     *                  up to getMaximumEncodeLength(sourcelen) bytes.
     *
     * @return The number of characters used in dest to store the encoded
     *         data. Excluding '\0' termination of the string (which is always
     *         added). -1 is returned if there was not enough space in the dest
     *         buffer to store all of the data.
     */
    static int encode(const char* source, int sourcelen,
                      char* dest, int destlen);

    /**
     * Decodes base64 data to binary format.
     *
     * @param source    The buffer to convert.
     * @param len       The length of the buffer.
     *
     * @return The base64 decoded string.
     *
     * @throws Throw IllegalArgumentException if source contains illegal base 64
     *         characters that are not whitespace.
     */
    static std::string decode(const std::string& source)
        { return decode(source.c_str(), source.size()); }

    /**
     * Decodes base64 data to binary format.
     *
     * @param source    The buffer to convert.
     * @param len       The length of the buffer.
     *
     * @return The base64 decoded string.
     */
    static std::string decode(const char* source, int len);

    /**
     * Decodes base 64 data in source to binary format written into dest.
     *
     * @param source    The input buffer.
     * @param sourcelen The length of the input buffer.
     * @param dest      The buffer to write the encoded data to.
     * @param destlen   The length of the output buffer.
     *
     * @return The number of bytes used in dest to store the binary
     *         representation, or -1 if there wasn't enough bytes available in
     *         dest.
     */
    static int decode(const char* source, int sourcelen,
                      char* dest, int destlen);
};

}  // namespace vespalib

