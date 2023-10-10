// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sha1.h"

/* #define LITEND * This should be #define'd if true. */
#define LITEND

#define rol(value, bits) (((value) << (bits)) | ((value) >> (32 - (bits))))

/* blk0() and blk() perform the initial expand. */
/* I got the idea of expanding during the round function from SSLeay */
#ifdef LITEND
#define blk0(i) (block->l[i] = (rol(block->l[i],24)&0xFF00FF00) \
    |(rol(block->l[i],8)&0x00FF00FF))
#else
#define blk0(i) block->l[i]
#endif
#define blk(i) (block->l[i&15] = rol(block->l[(i+13)&15]^block->l[(i+8)&15] \
    ^block->l[(i+2)&15]^block->l[i&15],1))

/* (R0+R1), R2, R3, R4 are the different operations used in SHA1 */
#define R0(v,w,x,y,z,i) z+=((w&(x^y))^y)+blk0(i)+0x5A827999+rol(v,5);w=rol(w,30);
#define R1(v,w,x,y,z,i) z+=((w&(x^y))^y)+blk(i)+0x5A827999+rol(v,5);w=rol(w,30);
#define R2(v,w,x,y,z,i) z+=(w^x^y)+blk(i)+0x6ED9EBA1+rol(v,5);w=rol(w,30);
#define R3(v,w,x,y,z,i) z+=(((w|x)&y)|(w&x))+blk(i)+0x8F1BBCDC+rol(v,5);w=rol(w,30);
#define R4(v,w,x,y,z,i) z+=(w^x^y)+blk(i)+0xCA62C1D6+rol(v,5);w=rol(w,30);

namespace vespalib {

void
Sha1::transform()
{
    uint32_t a, b, c, d, e;
    typedef union {
        uint8_t  c[64];
        uint32_t l[16];
    } CHAR64LONG16;
    CHAR64LONG16 *block = reinterpret_cast<CHAR64LONG16 *>(_buffer);
    /* Copy context->state[] to working vars */
    a = _state[0];
    b = _state[1];
    c = _state[2];
    d = _state[3];
    e = _state[4];
    /* 4 rounds of 20 operations each. Loop unrolled. */
    R0(a,b,c,d,e, 0); R0(e,a,b,c,d, 1); R0(d,e,a,b,c, 2); R0(c,d,e,a,b, 3);
    R0(b,c,d,e,a, 4); R0(a,b,c,d,e, 5); R0(e,a,b,c,d, 6); R0(d,e,a,b,c, 7);
    R0(c,d,e,a,b, 8); R0(b,c,d,e,a, 9); R0(a,b,c,d,e,10); R0(e,a,b,c,d,11);
    R0(d,e,a,b,c,12); R0(c,d,e,a,b,13); R0(b,c,d,e,a,14); R0(a,b,c,d,e,15);
    R1(e,a,b,c,d,16); R1(d,e,a,b,c,17); R1(c,d,e,a,b,18); R1(b,c,d,e,a,19);
    R2(a,b,c,d,e,20); R2(e,a,b,c,d,21); R2(d,e,a,b,c,22); R2(c,d,e,a,b,23);
    R2(b,c,d,e,a,24); R2(a,b,c,d,e,25); R2(e,a,b,c,d,26); R2(d,e,a,b,c,27);
    R2(c,d,e,a,b,28); R2(b,c,d,e,a,29); R2(a,b,c,d,e,30); R2(e,a,b,c,d,31);
    R2(d,e,a,b,c,32); R2(c,d,e,a,b,33); R2(b,c,d,e,a,34); R2(a,b,c,d,e,35);
    R2(e,a,b,c,d,36); R2(d,e,a,b,c,37); R2(c,d,e,a,b,38); R2(b,c,d,e,a,39);
    R3(a,b,c,d,e,40); R3(e,a,b,c,d,41); R3(d,e,a,b,c,42); R3(c,d,e,a,b,43);
    R3(b,c,d,e,a,44); R3(a,b,c,d,e,45); R3(e,a,b,c,d,46); R3(d,e,a,b,c,47);
    R3(c,d,e,a,b,48); R3(b,c,d,e,a,49); R3(a,b,c,d,e,50); R3(e,a,b,c,d,51);
    R3(d,e,a,b,c,52); R3(c,d,e,a,b,53); R3(b,c,d,e,a,54); R3(a,b,c,d,e,55);
    R3(e,a,b,c,d,56); R3(d,e,a,b,c,57); R3(c,d,e,a,b,58); R3(b,c,d,e,a,59);
    R4(a,b,c,d,e,60); R4(e,a,b,c,d,61); R4(d,e,a,b,c,62); R4(c,d,e,a,b,63);
    R4(b,c,d,e,a,64); R4(a,b,c,d,e,65); R4(e,a,b,c,d,66); R4(d,e,a,b,c,67);
    R4(c,d,e,a,b,68); R4(b,c,d,e,a,69); R4(a,b,c,d,e,70); R4(e,a,b,c,d,71);
    R4(d,e,a,b,c,72); R4(c,d,e,a,b,73); R4(b,c,d,e,a,74); R4(a,b,c,d,e,75);
    R4(e,a,b,c,d,76); R4(d,e,a,b,c,77); R4(c,d,e,a,b,78); R4(b,c,d,e,a,79);
    /* Add the working vars back into context.state[] */
    _state[0] += a;
    _state[1] += b;
    _state[2] += c;
    _state[3] += d;
    _state[4] += e;
    /* Wipe variables */
    a = b = c = d = e = 0;
}

Sha1::Sha1()
{
    reset();
}

void
Sha1::reset()
{
    /* SHA1 initialization constants */
    _state[0] = 0x67452301;
    _state[1] = 0xEFCDAB89;
    _state[2] = 0x98BADCFE;
    _state[3] = 0x10325476;
    _state[4] = 0xC3D2E1F0;
    _count[0] = _count[1] = 0;
}

void
Sha1::process(const char *data, size_t len)
{
    uint32_t i, j;
    j = (_count[0] >> 3) & 63;
    if ((_count[0] += len << 3) < (len << 3)) _count[1]++;
    _count[1] += (len >> 29);
    if ((j + len) > 63) {
        memcpy(&_buffer[j], data, (i = 64-j));
        transform();
        for ( ; i + 63 < len; i += 64) {
            memcpy(_buffer, &data[i], 64);
            transform();
        }
        j = 0;
    }
    else i = 0;
    memcpy(&_buffer[j], &data[i], len - i);
}

void
Sha1::get_digest(char *digest, size_t digestLen)
{
    uint32_t i, j;
    uint8_t finalcount[8];

    for (i = 0; i < 8; i++) {
        finalcount[i] =
            static_cast<uint8_t>((_count[(i >= 4 ? 0 : 1)]
                                  >> ((3-(i & 3)) * 8) ) & 255);  /* Endian independent */
    }
    process("\200", 1);
    while ((_count[0] & 504) != 448) {
        process("\0", 1);
    }
    process(reinterpret_cast<char *>(finalcount), 8);  /* Should cause a Transform() */
    if (digestLen > 20)
        digestLen = 20;
    for (i = 0; i < digestLen; i++) {
        digest[i] = static_cast<char>
                    ((_state[i>>2] >> ((3-(i & 3)) * 8) ) & 255);
    }
    /* Wipe variables */
    i = j = 0;
    memset(_buffer, 0, 64);
    memset(_state, 0, 20);
    memset(_count, 0, 8);
    memset(&finalcount, 0, 8);
}

void
Sha1::hash(const char *input, size_t inputLen,
           char *digest, size_t digestLen)
{
    Sha1 sha;
    sha.process(input, inputLen);
    sha.get_digest(digest, digestLen);
}

} // namespace vespalib
