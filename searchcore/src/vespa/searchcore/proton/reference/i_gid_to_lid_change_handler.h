// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_gid_to_lid_change_listener.h"
#include <vespa/searchlib/common/serialnum.h>
#include <set>
#include <vector>

namespace document { class GlobalId; }

namespace proton {

class IPendingGidToLidChanges;

/*
 * Interface class for registering listeners that get notification when
 * gid to lid mapping changes.
 */
class IGidToLidChangeHandler
{
public:
    using IDestructorCallbackSP = IGidToLidChangeListener::IDestructorCallbackSP;
    using SerialNum = search::SerialNum;
    using GlobalId = document::GlobalId;

    virtual ~IGidToLidChangeHandler();

    /*
     * Add listener unless a listener with matching docTypeName and
     * name already exists.
     */
    virtual void addListener(std::unique_ptr<IGidToLidChangeListener> listener) = 0;

    /**
     * Remove listeners with matching docTypeName unless name is present in
     * keepNames.
     */
    virtual void removeListeners(const vespalib::string &docTypeName,
                                 const std::set<vespalib::string> &keepNames) = 0;
    /**
     * Notify pending gid to lid mapping change. Passed on to listeners later
     * when force commit has made changes visible.
     */
    virtual void notifyPut(IDestructorCallbackSP context, GlobalId gid, uint32_t lid, SerialNum serial_num) = 0;
    /**
     * Notify removal of gid. Passed on to listeners at once.
     */
    void notifyRemove(IDestructorCallbackSP context, GlobalId gid, SerialNum serialNum);
    virtual void notifyRemoves(IDestructorCallbackSP context, const std::vector<GlobalId> & gids, SerialNum serialNum) = 0;
    virtual std::unique_ptr<IPendingGidToLidChanges> grab_pending_changes() = 0;
};

} // namespace proton
