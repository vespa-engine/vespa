// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include "multi_value_mapping2.h"
#include "multi_value_mapping2.hpp"
#include <vespa/vespalib/stllike/string.h>

LOG_SETUP(".searchlib.attribute.multivaluemapping2");

namespace search {
namespace attribute {

template class MultiValueMapping2<int32_t>;

} // namespace search::attribute
} // namespace search
