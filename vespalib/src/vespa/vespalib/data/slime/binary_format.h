// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "type.h"
#include "inserter.h"
#include <vespa/vespalib/data/output.h>
#include <vespa/vespalib/data/input_reader.h>
#include <vespa/vespalib/data/output_writer.h>
#include <string>

namespace vespalib { class Slime; }

namespace vespalib::slime {

struct BinaryFormat {
    static void encode(const Slime &slime, Output &output);
    static size_t decode(const Memory &memory, Slime &slime);
    static size_t decode_into(const Memory &memory, Slime &slime, const Inserter &inserter);
};

namespace binary_format {

inline uint64_t encode_zigzag(int64_t x) {
    return ((x << 1) ^ (x >> 63));
}

inline int64_t decode_zigzag(uint64_t x) {
    return ((x >> 1) ^ (-(x & 0x1)));
}

inline uint64_t encode_double(double x) {
    union { uint64_t UINT64; double DOUBLE; } val;
    val.DOUBLE = x;
    return val.UINT64;
}

inline double decode_double(uint64_t x) {
    union { uint64_t UINT64; double DOUBLE; } val;
    val.UINT64 = x;
    return val.DOUBLE;
}

inline char encode_type_and_meta(uint32_t type, uint32_t meta) {
    return (((type & 0x7) | (meta << 3)) & 0xff);
}

inline uint32_t decode_type(uint32_t type_and_meta) {
    return (type_and_meta & 0x7);
}

inline uint32_t decode_meta(uint32_t type_and_meta) {
    return ((type_and_meta >> 3) & 0x1f);
}


void write_cmpr_ulong(OutputWriter &out, uint64_t value);

inline uint32_t encode_cmpr_ulong(char *out, uint64_t value) {
    // pre-req: out has room for 10 bytes
    char *pos = out;
    char next = (value & 0x7f);
    value >>= 7;
    while (value != 0) {
        *pos++ = (next | 0x80);
        next = (value & 0x7f);
        value >>= 7;
    }
    *pos++ = next;
    return (pos - out);
}

inline uint64_t read_cmpr_ulong(InputReader &in) {
    uint64_t next = in.read();
    uint64_t value = (next & 0x7f);
    int shift = 7;
    while ((next & 0x80) != 0) {
        next = in.read();
        value |= ((next & 0x7f) << shift);
        shift += 7;
    }
    return value;
}

void write_type_and_size(OutputWriter &out, uint32_t type, uint64_t size);

inline uint64_t read_size(InputReader &in, uint32_t meta)
{
    return (meta == 0) ? read_cmpr_ulong(in) : (meta - 1);
}

template <bool top>
void write_type_and_bytes(OutputWriter &out,
                          uint32_t type, uint64_t bits)
{
    char *start = out.reserve(9); // max size
    char *pos = start + 1;
    while (bits != 0) {
        if (top) {
            *pos++ = (bits >> 56);
            bits <<= 8;
        } else {
            *pos++ = (bits & 0xff);
            bits >>= 8;
        }
    }
    *start = encode_type_and_meta(type, pos - start - 1);
    out.commit(pos - start);
}

template <bool top>
uint64_t read_bytes(InputReader &in, uint32_t bytes)
{
    uint64_t value = 0;
    int shift = top ? 56 : 0;
    for (uint32_t i = 0; i < bytes; ++i) {
        uint64_t byte = in.read();
        value |= ((byte & 0xff) << shift);
        if (top) {
            shift -= 8;
        } else {
            shift += 8;
        }
    }
    return value;
}

} // namespace vespalib::slime::binary_format

} // namespace vespalib::slime
