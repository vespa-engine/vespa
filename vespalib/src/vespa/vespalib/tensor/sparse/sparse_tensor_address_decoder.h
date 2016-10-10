// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "sparse_tensor_address_ref.h"

namespace vespalib {


namespace tensor {

/**
 * A decoder for a serialized tensor address, with only labels present.
 */
class SparseTensorAddressDecoder
{
    const char *_cur;
    const char *_end;
public:
    SparseTensorAddressDecoder(SparseTensorAddressRef ref)
        : _cur(static_cast<const char *>(ref.start())),
          _end(_cur + ref.size())
    {
    }

    bool valid() const { return _cur != _end; }

    void skipLabel() {
        while (*_cur != '\0') {
            ++_cur;
        }
        ++_cur;
    }
    vespalib::stringref decodeLabel() {
        const char *base = _cur;
        skipLabel();
        return vespalib::stringref(base, _cur - base - 1);
    }

};

} // namespace vespalib::tensor
} // namespace vespalib
