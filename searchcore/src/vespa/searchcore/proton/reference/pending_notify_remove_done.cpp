// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pending_notify_remove_done.h"
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <cassert>

namespace proton
{

PendingNotifyRemoveDone::PendingNotifyRemoveDone()
    : _gidToLidChangeHandler(nullptr),
      _gid(),
      _serialNum(0),
      _pending(false)
{
}

PendingNotifyRemoveDone::PendingNotifyRemoveDone(PendingNotifyRemoveDone &&rhs)
    : _gidToLidChangeHandler(rhs._gidToLidChangeHandler),
      _gid(rhs._gid),
      _serialNum(rhs._serialNum),
      _pending(rhs._pending)
{
    rhs._pending = false;
}

PendingNotifyRemoveDone::~PendingNotifyRemoveDone()
{
    assert(!_pending); // Fail if notifyRemoveDone is still pending
}

void
PendingNotifyRemoveDone::setup(IGidToLidChangeHandler &gidToLidChangeHandler, document::GlobalId gid, search::SerialNum serialNum)
{
    _gidToLidChangeHandler = &gidToLidChangeHandler;
    _gid = gid;
    _serialNum = serialNum;
    _pending = true;
}

void
PendingNotifyRemoveDone::invoke()
{
    if (_pending) {
        _gidToLidChangeHandler->notifyRemoveDone(_gid, _serialNum);
        _pending = false;
    }
}

}  // namespace proton
