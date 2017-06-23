// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search
{
namespace btree
{

class NoAggregated
{
public:
    NoAggregated() { }
    bool operator==(const NoAggregated &) const { return true; }
    bool operator!=(const NoAggregated &) const { return false; }
};


} // namespace search::btree
} // namespace search

