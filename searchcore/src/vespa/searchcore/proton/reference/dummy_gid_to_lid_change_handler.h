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
    ~DummyGidToLidChangeHandler() override;

    void notifyPut(IDestructorCallbackSP context, GlobalId gid, uint32_t lid, SerialNum serial_num) override;
    void notifyRemove(IDestructorCallbackSP context, GlobalId gid, SerialNum serialNum) override;
    void addListener(std::unique_ptr<IGidToLidChangeListener> listener) override;
    void removeListeners(const vespalib::string &docTypeName, const std::set<vespalib::string> &keepNames) override;
    std::unique_ptr<IPendingGidToLidChanges> grab_pending_changes() override;
};

} // namespace proton
