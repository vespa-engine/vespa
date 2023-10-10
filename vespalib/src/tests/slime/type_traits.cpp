// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "type_traits.h"

namespace vespalib {
namespace slime {

const bool TypeTraits<BOOL>::unsetValue;
const int64_t TypeTraits<LONG>::unsetValue;
const double TypeTraits<DOUBLE>::unsetValue = 0.0;
const Memory TypeTraits<STRING>::unsetValue;
const Memory TypeTraits<DATA>::unsetValue;

} // namespace vespalib::slime
} // namespace vespalib
