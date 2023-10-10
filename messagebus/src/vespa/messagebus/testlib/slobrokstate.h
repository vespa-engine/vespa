// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/messagebus/common.h>

namespace mbus {

class SlobrokState
{
public:
    using TYPE = std::vector<std::pair<string, uint32_t> >;
    using ITR = TYPE::const_iterator;

private:
    TYPE _data;

public:
    SlobrokState();
    SlobrokState &add(const string &pattern, uint32_t cnt = 1);
    ITR begin() const;
    ITR end() const;
};

} // namespace mbus

