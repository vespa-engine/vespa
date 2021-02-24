// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "serialnum.h"

namespace search {

/**
 * Class used to carry commit parameters.
 */
class CommitParam
{
public:
    CommitParam(SerialNum serialNum) noexcept : CommitParam(serialNum, false) {}
    CommitParam(SerialNum serialNum, bool forceUpdateStats) noexcept : CommitParam(serialNum, serialNum, forceUpdateStats) {}
    CommitParam(SerialNum firstSerialNum, SerialNum lastSerialNum, bool forceUpdateStats) noexcept
        : _firstSerialNum(firstSerialNum),
          _lastSerialNum(lastSerialNum),
          _forceUpdateStats(forceUpdateStats)
    {}

    bool forceUpdateStats() const { return _forceUpdateStats; }
    SerialNum firstSerialNum() const { return _firstSerialNum; }
    SerialNum lastSerialNum() const { return _lastSerialNum; }

private:
    const SerialNum _firstSerialNum;
    const SerialNum _lastSerialNum;
    const bool      _forceUpdateStats;
};

}
