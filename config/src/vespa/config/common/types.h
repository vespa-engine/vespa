// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/allocator.h>
#include <map>
#include <string>
#include <vector>

namespace config {

using StringVector = std::vector<std::string, vespalib::allocator_large<std::string>>;
using BoolVector = std::vector<bool>;
using DoubleVector = std::vector<double>;
using LongVector = std::vector<int64_t>;
using IntVector = std::vector<int32_t>;
using StringMap = std::map<std::string, std::string>;
using BoolMap = std::map<std::string, bool>;
using DoubleMap = std::map<std::string, double>;
using LongMap = std::map<std::string, int64_t>;
using IntMap = std::map<std::string, int32_t>;

}
