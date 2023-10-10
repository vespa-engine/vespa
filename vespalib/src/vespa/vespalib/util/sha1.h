// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstring>

namespace vespalib {

/**
 * SHA-1 in C
 * By Steve Reid <steve@edmweb.com>
 * 100% Public Domain
 *
 * To generate a digest for a message contained
 * in memory, simply use the static hash function. For incremental
 * digest generation, you need an instance of this class. If the
 * object has been used before, invoke reset to begin generating a new
 * digest. Then invoke process multiple times to process the
 * input. After all input has been given to the process function, use
 * the get_digest function to calculate the final digest.
 **/
class Sha1
{
private:
    uint32_t _state[5];
    uint32_t _count[2];
    uint8_t  _buffer[64];

    /**
     * Use the 64 bytes (512 bits) stored in _buffer to update
     * _state. This operation will garble the contents of buffer.
     **/
    void transform();

public:
    Sha1();

    /**
     * Start generating a new digest.
     **/
    void reset();

    /**
     * Process input data.
     *
     * @param data input data
     * @param len input data len
     **/
    void process(const char *data, size_t len);

    /**
     * Calculate final digest. By adjusting the digestLen parameter
     * this function may be instructed to only return a prefix of the
     * SHA-1 digest.
     *
     * @param digest where to put the digest
     * @param digestLen how much digest we want (max 20)
     **/
    void get_digest(char *digest, size_t digestLen = 20);

    /**
     * Calculate the SHA-1 digest prefix based on the given input buffer.
     *
     * @param buf input buffer
     * @param bufLen length of input buffer in bytes
     * @param digest where to put the digest prefix
     * @param digestLen the length of the wanted digest prefix (max 20)
     **/
    static void hash(const char *buf, size_t bufLen,
                     char *digest, size_t digestLen);
};

} // namespace vespalib
