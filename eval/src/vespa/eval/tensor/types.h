// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vector>
#include <map>

namespace vespalib {
namespace tensor {

using TensorCells = std::map<std::map<vespalib::string, vespalib::string>, double>;
using TensorDimensions = std::vector<vespalib::string>;
using TensorDimensionsSet = vespalib::hash_set<vespalib::string>;
using DenseTensorCells = std::map<std::map<vespalib::string, size_t>, double>;

} // namespace vespalib::tensor
} // namespace vespalib
