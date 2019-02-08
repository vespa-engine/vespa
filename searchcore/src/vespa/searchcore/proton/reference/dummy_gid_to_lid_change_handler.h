// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_gid_to_lid_change_handler.h"
#include <vector>
#include <mutex>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/document/base/globalid.h>

namespace searchcorespi { namespace index { struct IThreadService; } }

namespace proton {

/*
 * Dummy class for registering listeners that get notification when
 * gid to lid mapping changes.
 */
class DummyGidToLidChangeHandler : public IGidToLidChangeHandler
{
public:
    DummyGidToLidChangeHandler();
    virtual ~DummyGidToLidChangeHandler();

    virtual void notifyPutDone(GlobalId gid, uint32_t lid, SerialNum serialNum) override;
    virtual void notifyRemove(GlobalId gid, SerialNum serialNum) override;
    virtual void notifyRemoveDone(GlobalId gid, SerialNum serialNum) override;
    virtual void addListener(std::unique_ptr<IGidToLidChangeListener> listener) override;
    virtual void removeListeners(const vespalib::string &docTypeName,
                                 const std::set<vespalib::string> &keepNames) override;
};

} // namespace proton
