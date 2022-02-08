// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vector>
#include <map>

namespace config {

using StringVector = std::vector<vespalib::string, vespalib::allocator_large<vespalib::string>>;
using BoolVector = std::vector<bool>;
using DoubleVector = std::vector<double>;
using LongVector = std::vector<int64_t>;
using IntVector = std::vector<int32_t>;
using StringMap = std::map<vespalib::string, vespalib::string>;
using BoolMap = std::map<vespalib::string, bool>;
using DoubleMap = std::map<vespalib::string, double>;
using LongMap = std::map<vespalib::string, int64_t>;
using IntMap = std::map<vespalib::string, int32_t>;

}
