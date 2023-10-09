// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slobrokstate.h"

namespace mbus {

SlobrokState::SlobrokState()
    : _data()
{ }

SlobrokState &
SlobrokState::add(const string &pattern, uint32_t cnt)
{
    _data.push_back(std::make_pair(pattern, cnt));
    return *this;
}

SlobrokState::ITR
SlobrokState::begin() const
{
    return _data.begin();
}

SlobrokState::ITR
SlobrokState::end() const
{
    return _data.end();
}

} // namespace mbus
