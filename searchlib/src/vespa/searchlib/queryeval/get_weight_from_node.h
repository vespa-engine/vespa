// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/weight.h>

namespace search {
namespace query { class Node; }
namespace queryeval {

search::query::Weight getWeightFromNode(const search::query::Node &node);

} // namespace search::queryeval
} // namespace search

