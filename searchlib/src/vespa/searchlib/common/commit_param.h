// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serialnum.h"

namespace search {

/**
 * Class used to carry commit parameters.
 */
struct CommitParam
{
    CommitParam(SerialNum serialNum) noexcept : CommitParam(serialNum, false) {}
    CommitParam(SerialNum serialNum, bool forceUpdateStats) noexcept : CommitParam(serialNum, serialNum, forceUpdateStats) {}
    CommitParam(SerialNum firstSerialNum, SerialNum lastSerialNum, bool forceUpdateStats) noexcept
        : _firstSerialNum(firstSerialNum),
          _lastSerialNum(lastSerialNum),
          _forceUpdateStats(forceUpdateStats)
    {}

    const SerialNum _firstSerialNum;
    const SerialNum _lastSerialNum;
    const bool      _forceUpdateStats;
};

}
