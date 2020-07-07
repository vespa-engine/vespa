// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_gid_to_lid_change_handler.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/document/base/globalid.h>
#include <vector>
#include <mutex>

namespace searchcorespi { namespace index { struct IThreadService; } }

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
    struct PendingRemoveEntry {
        SerialNum removeSerialNum;
        SerialNum putSerialNum;
        uint32_t  refCount;

        PendingRemoveEntry(SerialNum removeSerialNum_)
            : removeSerialNum(removeSerialNum_),
              putSerialNum(0),
              refCount(1)
        {
        }

        PendingRemoveEntry()
            : PendingRemoveEntry(0)
        {
        }
    };

    std::mutex _lock;
    Listeners  _listeners;
    bool       _closed;
    vespalib::hash_map<GlobalId, PendingRemoveEntry, GlobalId::hash> _pendingRemove;

    void notifyPutDone(Context context, GlobalId gid, uint32_t lid);
    void notifyRemove(Context context, GlobalId gid);
public:
    GidToLidChangeHandler();
    ~GidToLidChangeHandler() override;

    void notifyPutDone(Context context, GlobalId gid, uint32_t lid, SerialNum serialNum) override;
    void notifyRemove(Context context, GlobalId gid, SerialNum serialNum) override;
    void notifyRemoveDone(GlobalId gid, SerialNum serialNum) override;

    /**
     * Close handler, further notifications are blocked.
     */
    void close();

    void addListener(std::unique_ptr<IGidToLidChangeListener> listener) override;
    void removeListeners(const vespalib::string &docTypeName, const std::set<vespalib::string> &keepNames) override;
};

} // namespace proton
