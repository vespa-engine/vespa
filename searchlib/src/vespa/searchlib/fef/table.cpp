// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "table.h"
#include <limits>

namespace search::fef {

Table::Table() :
    _table(),
    _max(-std::numeric_limits<double>::max())
{
    _table.reserve(256);
}

Table::~Table() = default;

}
