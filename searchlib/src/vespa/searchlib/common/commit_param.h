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
    enum class UpdateStats { SKIP, FORCE};
    CommitParam(SerialNum serialNum) noexcept : CommitParam(serialNum, UpdateStats::SKIP) {}
    CommitParam(SerialNum serialNum, UpdateStats updateStats) noexcept : CommitParam(serialNum, serialNum, updateStats) {}
    CommitParam(SerialNum firstSerialNum, SerialNum lastSerialNum, UpdateStats updateStats) noexcept
        : _firstSerialNum(firstSerialNum),
          _lastSerialNum(lastSerialNum),
          _updateStats(updateStats)
    {}

    bool forceUpdateStats() const { return _updateStats == UpdateStats::FORCE; }
    SerialNum firstSerialNum() const { return _firstSerialNum; }
    SerialNum lastSerialNum() const { return _lastSerialNum; }

private:
    const SerialNum    _firstSerialNum;
    const SerialNum    _lastSerialNum;
    const UpdateStats  _updateStats;
};

}
