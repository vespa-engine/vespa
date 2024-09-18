// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zc_decoder.h"
#include <span>

namespace search::diskindex {

/*
 * Class for decoding a stream of step coded numbers, used when
 * validating file content during sequential read.
 */
class ZcDecoderValidator : public ZcDecoder {
    std::span<const uint8_t> _buffer;
public:
    ZcDecoderValidator() noexcept
        : ZcDecoder(),
          _buffer()
    {
    }

    ZcDecoderValidator(std::span<const uint8_t> buffer) noexcept
        : ZcDecoder(buffer.data()),
          _buffer(buffer)
    {
    }

    size_t pos() const noexcept { return _cur - _buffer.data(); }
    bool at_end() const noexcept { return pos() == _buffer.size(); }
    bool before_end() const noexcept { return pos() < _buffer.size(); }
};

}
