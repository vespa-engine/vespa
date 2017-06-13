// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 *
 * Generic hash functions to ensure the same hash values are used everywhere.
 */

/*
 * The following copyright notice applies to the code between the
 * "--- START OF MD5 CODE ---" and "--- END OF MD5 CODE ---" markers:
 *
 *  MD5C.C - RSA Data Security, Inc., MD5 message-digest algorithm
 *
 * Copyright (C) 1991-2, RSA Data Security, Inc. Created 1991. All
 * rights reserved.
 *
 * License to copy and use this software is granted provided that it
 * is identified as the "RSA Data Security, Inc. MD5 Message-Digest
 * Algorithm" in all material mentioning or referencing this software
 * or this function.
 *
 * License is also granted to make and use derivative works provided
 * that such works are identified as "derived from the RSA Data
 * Security, Inc. MD5 Message-Digest Algorithm" in all material
 * mentioning or referencing the derived work.
 *
 * RSA Data Security, Inc. makes no representations concerning either
 * the merchantability of this software or the suitability of this
 * software for any particular purpose. It is provided "as is"
 * without express or implied warranty of any kind.
 *
 * These notices must be retained in any copies of any part of this
 * documentation and/or software.
 *
 */

#include "md5.h"
#include <string.h>
#include <inttypes.h>

/* --- START OF MD5 CODE --- */

#define S11 7
#define S12 12
#define S13 17
#define S14 22
#define S21 5
#define S22 9
#define S23 14
#define S24 20
#define S31 4
#define S32 11
#define S33 16
#define S34 23
#define S41 6
#define S42 10
#define S43 15
#define S44 21

/* MD5 context. */
typedef struct
{
  uint32_t state[4];                                   /* state (ABCD) */
  uint32_t count[2];        /* number of bits, modulo 2^64 (lsb first) */
  unsigned char buffer[64];                         /* input buffer */
} MD5_CTX;

static void MD5Init (MD5_CTX *);
static void MD5Update (MD5_CTX *, const unsigned char *, uint32_t);
static void MD5Final (unsigned char [16], MD5_CTX *);
static void MD5Transform (uint32_t [4], const unsigned char [64]);
static void Encode (unsigned char *, uint32_t *, uint32_t);
static void Decode (uint32_t *, const unsigned char *, uint32_t);

static unsigned char PADDING[64] = {
	0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
	0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
};

/* F, G, H and I are basic MD5 functions.
 */
#define F(x, y, z) (((x) & (y)) | ((~x) & (z)))
#define G(x, y, z) (((x) & (z)) | ((y) & (~z)))
#define H(x, y, z) ((x) ^ (y) ^ (z))
#define I(x, y, z) ((y) ^ ((x) | (~z)))

/* ROTATE_LEFT rotates x left n bits.
 */
#define ROTATE_LEFT(x, n) (((x) << (n)) | ((x) >> (32-(n))))

/* FF, GG, HH, and II transformations for rounds 1, 2, 3, and 4.
Rotation is separate from addition to prevent recomputation.
 */
#define FF(a, b, c, d, x, s, ac) { \
    (a) += F ((b), (c), (d)) + (x) + (uint32_t)(ac); \
    (a) = ROTATE_LEFT ((a), (s)); \
    (a) += (b); \
}
#define GG(a, b, c, d, x, s, ac) { \
    (a) += G ((b), (c), (d)) + (x) + (uint32_t)(ac); \
    (a) = ROTATE_LEFT ((a), (s)); \
    (a) += (b); \
}
#define HH(a, b, c, d, x, s, ac) { \
    (a) += H ((b), (c), (d)) + (x) + (uint32_t)(ac); \
    (a) = ROTATE_LEFT ((a), (s)); \
    (a) += (b); \
}
#define II(a, b, c, d, x, s, ac) { \
    (a) += I ((b), (c), (d)) + (x) + (uint32_t)(ac); \
    (a) = ROTATE_LEFT ((a), (s)); \
    (a) += (b); \
}

/* MD5 initialization. Begins an MD5 operation, writing a new context.
 */
static void
MD5Init (MD5_CTX *context)
{
    context->count[0] = context->count[1] = 0;
    /* Load magic initialization constants.
     */
    context->state[0] = 0x67452301ul;
    context->state[1] = 0xefcdab89ul;
    context->state[2] = 0x98badcfeul;
    context->state[3] = 0x10325476ul;
}

/* MD5 block update operation. Continues an MD5 message-digest
  operation, processing another message block, and updating the
  context.
 */
static void
MD5Update (MD5_CTX *context, const unsigned char *input, uint32_t inputLen)
{
    uint32_t i, idx, partLen;

    /* Compute number of bytes mod 64 */
    idx = (uint32_t)((context->count[0] >> 3) & 0x3F);

    /* Update number of bits */
    if ((context->count[0] += ((uint32_t)inputLen << 3)) < ((uint32_t)inputLen << 3)) {
        context->count[1]++;
    }
    context->count[1] += ((uint32_t)inputLen >> 29);

    partLen = 64 - idx;

    /* Transform as many times as possible.
     */
    if (inputLen >= partLen) {
        memcpy((unsigned char *)&context->buffer[idx], (const unsigned char *)input, partLen);
        MD5Transform(context->state, context->buffer);

        for (i = partLen; i + 63 < inputLen; i += 64) {
            MD5Transform (context->state, &input[i]);
        }
        idx = 0;
    } else {
        i = 0;
    }

    /* Buffer remaining input */
    memcpy((unsigned char *)&context->buffer[idx], (const unsigned char *)&input[i], inputLen-i);
}

/* MD5 finalization. Ends an MD5 message-digest operation, writing the
  the message digest and zeroizing the context.
 */
static void
MD5Final (unsigned char digest[16], MD5_CTX *context)
{
    unsigned char bits[8];
    uint32_t idx, padLen;

    /* Save number of bits */
    Encode (bits, context->count, 8);

    /* Pad out to 56 mod 64.
     */
    idx = (uint32_t)((context->count[0] >> 3) & 0x3f);
    padLen = (idx < 56) ? (56 - idx) : (120 - idx);
    MD5Update (context, PADDING, padLen);

    /* Append length (before padding) */
    MD5Update (context, bits, 8);
    /* Store state in digest */
    Encode (digest, context->state, 16);

    /* Zeroize sensitive information.
     */
    memset ((unsigned char *)context, 0, sizeof (*context));
}

/* MD5 basic transformation. Transforms state based on block.
 */
static void
MD5Transform (uint32_t state[4], const unsigned char dblock[64])
{
    uint32_t a = state[0], b = state[1], c = state[2], d = state[3], x[16];

    Decode (x, dblock, 64);

    /* Round 1 */
    FF (a, b, c, d, x[ 0], S11, 0xd76aa478ul); /* 1 */
    FF (d, a, b, c, x[ 1], S12, 0xe8c7b756ul); /* 2 */
    FF (c, d, a, b, x[ 2], S13, 0x242070dbul); /* 3 */
    FF (b, c, d, a, x[ 3], S14, 0xc1bdceeeul); /* 4 */
    FF (a, b, c, d, x[ 4], S11, 0xf57c0faful); /* 5 */
    FF (d, a, b, c, x[ 5], S12, 0x4787c62aul); /* 6 */
    FF (c, d, a, b, x[ 6], S13, 0xa8304613ul); /* 7 */
    FF (b, c, d, a, x[ 7], S14, 0xfd469501ul); /* 8 */
    FF (a, b, c, d, x[ 8], S11, 0x698098d8ul); /* 9 */
    FF (d, a, b, c, x[ 9], S12, 0x8b44f7aful); /* 10 */
    FF (c, d, a, b, x[10], S13, 0xffff5bb1ul); /* 11 */
    FF (b, c, d, a, x[11], S14, 0x895cd7beul); /* 12 */
    FF (a, b, c, d, x[12], S11, 0x6b901122ul); /* 13 */
    FF (d, a, b, c, x[13], S12, 0xfd987193ul); /* 14 */
    FF (c, d, a, b, x[14], S13, 0xa679438eul); /* 15 */
    FF (b, c, d, a, x[15], S14, 0x49b40821ul); /* 16 */

    /* Round 2 */
    GG (a, b, c, d, x[ 1], S21, 0xf61e2562ul); /* 17 */
    GG (d, a, b, c, x[ 6], S22, 0xc040b340ul); /* 18 */
    GG (c, d, a, b, x[11], S23, 0x265e5a51ul); /* 19 */
    GG (b, c, d, a, x[ 0], S24, 0xe9b6c7aaul); /* 20 */
    GG (a, b, c, d, x[ 5], S21, 0xd62f105dul); /* 21 */
    GG (d, a, b, c, x[10], S22,  0x2441453ul); /* 22 */
    GG (c, d, a, b, x[15], S23, 0xd8a1e681ul); /* 23 */
    GG (b, c, d, a, x[ 4], S24, 0xe7d3fbc8ul); /* 24 */
    GG (a, b, c, d, x[ 9], S21, 0x21e1cde6ul); /* 25 */
    GG (d, a, b, c, x[14], S22, 0xc33707d6ul); /* 26 */
    GG (c, d, a, b, x[ 3], S23, 0xf4d50d87ul); /* 27 */
    GG (b, c, d, a, x[ 8], S24, 0x455a14edul); /* 28 */
    GG (a, b, c, d, x[13], S21, 0xa9e3e905ul); /* 29 */
    GG (d, a, b, c, x[ 2], S22, 0xfcefa3f8ul); /* 30 */
    GG (c, d, a, b, x[ 7], S23, 0x676f02d9ul); /* 31 */
    GG (b, c, d, a, x[12], S24, 0x8d2a4c8aul); /* 32 */

    /* Round 3 */
    HH (a, b, c, d, x[ 5], S31, 0xfffa3942ul); /* 33 */
    HH (d, a, b, c, x[ 8], S32, 0x8771f681ul); /* 34 */
    HH (c, d, a, b, x[11], S33, 0x6d9d6122ul); /* 35 */
    HH (b, c, d, a, x[14], S34, 0xfde5380cul); /* 36 */
    HH (a, b, c, d, x[ 1], S31, 0xa4beea44ul); /* 37 */
    HH (d, a, b, c, x[ 4], S32, 0x4bdecfa9ul); /* 38 */
    HH (c, d, a, b, x[ 7], S33, 0xf6bb4b60ul); /* 39 */
    HH (b, c, d, a, x[10], S34, 0xbebfbc70ul); /* 40 */
    HH (a, b, c, d, x[13], S31, 0x289b7ec6ul); /* 41 */
    HH (d, a, b, c, x[ 0], S32, 0xeaa127faul); /* 42 */
    HH (c, d, a, b, x[ 3], S33, 0xd4ef3085ul); /* 43 */
    HH (b, c, d, a, x[ 6], S34,  0x4881d05ul); /* 44 */
    HH (a, b, c, d, x[ 9], S31, 0xd9d4d039ul); /* 45 */
    HH (d, a, b, c, x[12], S32, 0xe6db99e5ul); /* 46 */
    HH (c, d, a, b, x[15], S33, 0x1fa27cf8ul); /* 47 */
    HH (b, c, d, a, x[ 2], S34, 0xc4ac5665ul); /* 48 */

    /* Round 4 */
    II (a, b, c, d, x[ 0], S41, 0xf4292244ul); /* 49 */
    II (d, a, b, c, x[ 7], S42, 0x432aff97ul); /* 50 */
    II (c, d, a, b, x[14], S43, 0xab9423a7ul); /* 51 */
    II (b, c, d, a, x[ 5], S44, 0xfc93a039ul); /* 52 */
    II (a, b, c, d, x[12], S41, 0x655b59c3ul); /* 53 */
    II (d, a, b, c, x[ 3], S42, 0x8f0ccc92ul); /* 54 */
    II (c, d, a, b, x[10], S43, 0xffeff47dul); /* 55 */
    II (b, c, d, a, x[ 1], S44, 0x85845dd1ul); /* 56 */
    II (a, b, c, d, x[ 8], S41, 0x6fa87e4ful); /* 57 */
    II (d, a, b, c, x[15], S42, 0xfe2ce6e0ul); /* 58 */
    II (c, d, a, b, x[ 6], S43, 0xa3014314ul); /* 59 */
    II (b, c, d, a, x[13], S44, 0x4e0811a1ul); /* 60 */
    II (a, b, c, d, x[ 4], S41, 0xf7537e82ul); /* 61 */
    II (d, a, b, c, x[11], S42, 0xbd3af235ul); /* 62 */
    II (c, d, a, b, x[ 2], S43, 0x2ad7d2bbul); /* 63 */
    II (b, c, d, a, x[ 9], S44, 0xeb86d391ul); /* 64 */

    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;

    /* Zeroize sensitive information.
     */
    memset((unsigned char *)x, 0, sizeof (x));
}

static void
Encode (unsigned char *output, uint32_t *input, uint32_t len)
{
    uint32_t i, j;

    for (i = 0, j = 0; j < len; i++, j += 4) {
        output[j] = (unsigned char)(input[i] & 0xff);
        output[j+1] = (unsigned char)((input[i] >> 8) & 0xff);
        output[j+2] = (unsigned char)((input[i] >> 16) & 0xff);
        output[j+3] = (unsigned char)((input[i] >> 24) & 0xff);
    }
}

/* Decodes input (unsigned char) into output (uint32_t). Assumes len is
  a multiple of 4.
 */

static void
Decode (uint32_t *output, const unsigned char *input, uint32_t len)
{
    uint32_t i, j;

    for (i = 0, j = 0; j < len; i++, j += 4)
        output[i] = ((uint32_t)input[j]) | (((uint32_t)input[j+1]) << 8) |
            (((uint32_t)input[j+2]) << 16) | (((uint32_t)input[j+3]) << 24);
}


/* --- END OF MD5 CODE --- */

void fastc_md5sum(const void *s, size_t len, unsigned char *key)
{
	MD5_CTX m5;
	MD5Init(&m5);
	MD5Update(&m5, (const unsigned char *)s, len);
	MD5Final(key, &m5);
}

