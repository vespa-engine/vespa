// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>

namespace search::diskindex {

/*
 * Class for decoding a stream of step coded numbers.
 * Handles values up to ((1ul << 42) - 1), i.e. 6 byte sequence
 */
class ZcDecoder {
protected:
    const uint8_t* _cur;
    static constexpr uint8_t mark = 1 << 7;
    static constexpr uint8_t mask = mark - 1;

public:
    ZcDecoder() noexcept
        : _cur(nullptr)
    {
    }

    ZcDecoder(const uint8_t* cur) noexcept
        : _cur(cur)
    {
    }

    void set_cur(const uint8_t* cur) noexcept { _cur = cur; }

    uint64_t decode42() noexcept {
        const uint8_t *cur = _cur;
        if (cur[0] < mark) [[likely]] {
            _cur = cur + 1;
            return cur[0];
        } else if (cur[1] < mark) [[likely]] {
            _cur = cur + 2;
            return (cur[0] & mask) + (cur[1] << 7);
        } else if (cur[2] < mark) [[likely]] {
            _cur = cur + 3;
            return (cur[0] & mask) + ((cur[1] & mask) << 7) + (cur[2] << 14);
        } else if (cur[3] < mark) [[likely]] {
            _cur = cur + 4;
            return (cur[0] & mask) + ((cur[1] & mask) << 7) + ((cur[2] & mask) << 14) + (cur[3] << 21);
        } else if (cur[4] < mark) [[likely]] {
            _cur = cur + 5;
            return (cur[0] & mask) + ((cur[1] & mask) << 7) + ((cur[2] & mask) << 14) + ((cur[3] & mask) << 21) +
                   (static_cast<uint64_t>(cur[4]) << 28);
        } else {
            _cur = cur + 6;
            return (cur[0] & mask) + ((cur[1] & mask) << 7) + ((cur[2] & mask) << 14) + ((cur[3] & mask) << 21) +
                   (static_cast<uint64_t>((cur[4] & mask) + (cur[5] << 7)) << 28);
        }
    }

    uint32_t decode32() noexcept {
        const uint8_t *cur = _cur;
        if (cur[0] < mark) [[likely]] {
            _cur = cur + 1;
            return cur[0];
        } else if (cur[1] < mark) [[likely]] {
            _cur = cur + 2;
            return (cur[0] & mask) + (cur[1] << 7);
        } else if (cur[2] < mark) [[likely]] {
            _cur = cur + 3;
            return (cur[0] & mask) + ((cur[1] & mask) << 7) + (cur[2] << 14);
        } else if (cur[3] < mark) [[likely]] {
            _cur = cur + 4;
            return (cur[0] & mask) + ((cur[1] & mask) << 7) + ((cur[2] & mask) << 14) + (cur[3] << 21);
        } else {
            _cur = cur + 5;
            return (cur[0] & mask) + ((cur[1] & mask) << 7) + ((cur[2] & mask) << 14) + ((cur[3] & mask) << 21) +
                   (cur[4] << 28);
        }
    }
};

}
