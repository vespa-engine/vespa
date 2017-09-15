// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton
{

class IGidToLidChangeHandler;

/*
 * Class used to keep track of a pending notifyRemoveDone() call to
 * a gid to lid change handler.
 */
class PendingNotifyRemoveDone
{
    IGidToLidChangeHandler *_gidToLidChangeHandler;
    document::GlobalId      _gid;
    search::SerialNum       _serialNum;
    bool                    _pending;

public:
    PendingNotifyRemoveDone();
    PendingNotifyRemoveDone(PendingNotifyRemoveDone &&rhs);
    PendingNotifyRemoveDone(const PendingNotifyRemoveDone &rhs) = delete;
    PendingNotifyRemoveDone &operator=(const PendingNotifyRemoveDone &rhs) = delete;
    PendingNotifyRemoveDone &operator=(PendingNotifyRemoveDone &&rhs) = delete;
    ~PendingNotifyRemoveDone();
    void setup(IGidToLidChangeHandler &gidToLidChangeHandler, document::GlobalId gid, search::SerialNum serialNum);
    void invoke();
};

}  // namespace proton
