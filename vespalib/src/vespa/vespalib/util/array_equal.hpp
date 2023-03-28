// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "array.h"

namespace vespalib {

template <typename T>
bool Array<T>::operator ==(const Array & rhs) const noexcept
{
    bool retval(size() == rhs.size());
    for (size_t i(0); retval && (i < _sz); i++) {
        if ( ! (*array(i) == rhs[i]) ) {
            retval = false;
        }
    }
    return retval;
}

template <typename T>
bool Array<T>::operator != (const Array & rhs) const noexcept {
    return !(*this == rhs);
}

}
