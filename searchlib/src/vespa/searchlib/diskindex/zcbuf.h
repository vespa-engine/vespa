// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>

namespace search::diskindex {

/*
 * Class containing Zc-encoded data in a memory buffer, typically
 * docid deltas and skip information for posting lists.
 */
class ZcBuf
{
public:
    uint8_t *_valI;
    uint8_t *_valE;
    uint8_t *_mallocStart;
    size_t _mallocSize;

    ZcBuf();
    ~ZcBuf();

    static size_t zcSlack() { return 4; }
    void clearReserve(size_t reserveSize);
    void clear() { _valI = _mallocStart; }
    size_t capacity() const { return _valE - _mallocStart; }
    size_t size() const { return _valI - _mallocStart; }
    size_t pos() const { return _valI - _mallocStart; }
    void expand();

    void maybeExpand() {
        if (__builtin_expect(_valI >= _valE, false)) {
            expand();
        }
    }

    void encode(uint32_t num) {
        for (;;) {
            if (num < (1 << 7)) {
                *_valI++ = num;
                break;
            }
            *_valI++ = (num & ((1 << 7) - 1)) | (1 << 7);
            num >>= 7;
        }
        maybeExpand();
    }

    uint32_t decode() {
        uint32_t res;
        uint8_t *valI = _valI;
        if (__builtin_expect(valI[0] < (1 << 7), true)) {
            res = valI[0];
            valI += 1;
        } else if (__builtin_expect(valI[1] < (1 << 7), true)) {
            res = (valI[0] & ((1 << 7) - 1)) +
                  (valI[1] << 7);
            valI += 2;
        } else if (__builtin_expect(valI[2] < (1 << 7), true)) {
            res = (valI[0] & ((1 << 7) - 1)) +
                  ((valI[1] & ((1 << 7) - 1)) << 7) +
                  (valI[2] << 14);
            valI += 3;
        } else if (__builtin_expect(valI[3] < (1 << 7), true)) {
            res = (valI[0] & ((1 << 7) - 1)) +
                  ((valI[1] & ((1 << 7) - 1)) << 7) +
                  ((valI[2] & ((1 << 7) - 1)) << 14) +
                  (valI[3] << 21);
            valI += 4;
        } else {
            res = (valI[0] & ((1 << 7) - 1)) +
                  ((valI[1] & ((1 << 7) - 1)) << 7) +
                  ((valI[2] & ((1 << 7) - 1)) << 14) +
                  ((valI[3] & ((1 << 7) - 1)) << 21) +
                  (valI[4] << 28);
            valI += 5;
        }
        _valI = valI;
        return res;
    }
};

}
