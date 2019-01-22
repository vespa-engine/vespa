// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utils.hpp"

namespace search::features::util {

template double strToNum<double>(vespalib::stringref str);
template uint32_t strToNum<uint32_t>(vespalib::stringref str);
template uint64_t strToNum<uint64_t>(vespalib::stringref str);
template int32_t strToNum<int32_t>(vespalib::stringref str);
template int64_t strToNum<int64_t>(vespalib::stringref str);

}
