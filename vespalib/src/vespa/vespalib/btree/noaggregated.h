// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::btree {

class NoAggregated
{
public:
    NoAggregated() { }
    bool operator==(const NoAggregated &) const { return true; }
    bool operator!=(const NoAggregated &) const { return false; }
};

}
