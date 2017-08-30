// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_gid_to_lid_change_handler.h"
#include <vector>
#include <mutex>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/document/base/globalid.h>

namespace searchcorespi { namespace index { class IThreadService; } }

namespace proton {

/*
 * Class for registering listeners that get notification when
 * gid to lid mapping changes. Also handles notifications which have
 * to be executed by master thread service.
 */
class GidToLidChangeHandler : public std::enable_shared_from_this<GidToLidChangeHandler>,
                              public IGidToLidChangeHandler
{
    using lock_guard = std::lock_guard<std::mutex>;
    using Listeners = std::vector<std::unique_ptr<IGidToLidChangeListener>>;
    std::mutex _lock;
    Listeners _listeners;
    bool _closed;
    vespalib::hash_map<GlobalId, SerialNum, GlobalId::hash> _pendingRemove;

    void notifyGidToLidChange(GlobalId gid, uint32_t lid);
public:
    GidToLidChangeHandler();
    virtual ~GidToLidChangeHandler();

    /**
     * Notify gid to lid mapping change.
     */
    virtual void notifyPut(GlobalId gid, uint32_t lid, SerialNum serialNum) override;
    virtual void notifyRemove(GlobalId gid, SerialNum serialNum) override;
    virtual void notifyRemoveDone(GlobalId gid, SerialNum serialNum) override;

    /**
     * Close handler, further notifications are blocked.
     */
    void close();

    /*
     * Add listener unless a listener with matching docTypeName and
     * name already exists.
     */
    virtual void addListener(std::unique_ptr<IGidToLidChangeListener> listener) override;

    /**
     * Remove listeners with matching docTypeName unless name is present in
     * keepNames.
     */
    virtual void removeListeners(const vespalib::string &docTypeName,
                                 const std::set<vespalib::string> &keepNames) override;
};

} // namespace proton
