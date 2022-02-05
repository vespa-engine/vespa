// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <map>

namespace config {

typedef std::vector<vespalib::string> StringVector;
typedef std::vector<bool> BoolVector;
typedef std::vector<double> DoubleVector;
typedef std::vector<int64_t> LongVector;
typedef std::vector<int32_t> IntVector;
typedef std::map<vespalib::string, vespalib::string> StringMap;
typedef std::map<vespalib::string, bool> BoolMap;
typedef std::map<vespalib::string, double> DoubleMap;
typedef std::map<vespalib::string, int64_t> LongMap;
typedef std::map<vespalib::string, int32_t> IntMap;

} // namespace common

