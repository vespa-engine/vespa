// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/weight.h>

namespace search::query { class Node; }
namespace search::queryeval {

search::query::Weight getWeightFromNode(const search::query::Node &node);

}

