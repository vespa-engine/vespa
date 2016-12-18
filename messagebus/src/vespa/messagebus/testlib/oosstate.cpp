// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "oosstate.h"

namespace mbus {

OOSState::OOSState()
    : _data()
{ }

OOSState &
OOSState::add(const string &service, bool oos)
{
    _data.push_back(std::make_pair(service, oos));
    return *this;
}

OOSState::ITR
OOSState::begin() const
{
    return _data.begin();
}

OOSState::ITR
OOSState::end() const
{
    return _data.end();
}

} // namespace mbus
