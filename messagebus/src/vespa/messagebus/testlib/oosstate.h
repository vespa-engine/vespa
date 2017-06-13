// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <vespa/messagebus/common.h>

namespace mbus {

class OOSState
{
public:
    typedef std::vector<std::pair<string, bool> > TYPE;
    typedef TYPE::const_iterator ITR;

private:
    TYPE _data;

public:
    OOSState();
    OOSState &add(const string &service, bool oos = true);
    ITR begin() const;
    ITR end() const;
};

} // namespace mbus

